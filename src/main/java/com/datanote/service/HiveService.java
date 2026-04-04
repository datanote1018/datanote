package com.datanote.service;

import com.datanote.common.Constants;
import com.datanote.config.HiveConfig;
import com.datanote.model.ColumnInfo;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import org.apache.hive.jdbc.HiveStatement;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.sql.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * Hive 服务 — DDL 生成/执行、HiveSQL 查询
 */
@Service
@RequiredArgsConstructor
public class HiveService {

    private static final Logger log = LoggerFactory.getLogger(HiveService.class);

    private final HiveConfig hiveConfig;
    private final SimpMessagingTemplate messagingTemplate;

    @Value("${hive.warehouse}")
    private String hiveWarehouse;

    /**
     * 生成 Hive ODS 建表 DDL
     */
    public String generateDDL(String sourceDb, String sourceTable, List<ColumnInfo> columns, String syncMode) {
        String odsTable = getOdsTableName(sourceDb, sourceTable, syncMode);
        StringBuilder ddl = new StringBuilder();
        ddl.append("CREATE TABLE IF NOT EXISTS ods.").append(odsTable).append(" (\n");

        for (int i = 0; i < columns.size(); i++) {
            ColumnInfo col = columns.get(i);
            String colName = col.getName().toLowerCase().replaceAll("[^a-z0-9_]", "");
            ddl.append("  ").append(colName).append(" STRING");
            if (col.getComment() != null && !col.getComment().isEmpty()) {
                String safeComment = col.getComment().replace("\\", "\\\\").replace("'", "\\'");
                ddl.append(" COMMENT '").append(safeComment).append("'");
            }
            if (i < columns.size() - 1) {
                ddl.append(",");
            }
            ddl.append("\n");
        }

        ddl.append(")\n");
        ddl.append("COMMENT 'ODS层 ").append(sourceDb).append(".").append(sourceTable).append(" 全量同步'\n");
        ddl.append("PARTITIONED BY (dt STRING COMMENT '同步日期')\n");
        ddl.append("ROW FORMAT DELIMITED FIELDS TERMINATED BY '\\t'\n");
        ddl.append("STORED AS ORC\n");
        ddl.append("TBLPROPERTIES ('orc.compress'='SNAPPY')");

        return ddl.toString();
    }

    /**
     * 执行 Hive DDL 建表
     */
    public void executeDDL(String ddl) throws Exception {
        log.info("执行 Hive DDL:\n{}", ddl);
        try (Connection conn = hiveConfig.getConnection();
             Statement stmt = conn.createStatement()) {
            // 先创建 ods 库（如果不存在）
            stmt.execute("CREATE DATABASE IF NOT EXISTS ods");
            stmt.execute(ddl);
            log.info("Hive 建表成功");
        }
    }

    /**
     * 执行 HiveSQL 查询，返回列名 + 数据行
     */
    public Map<String, Object> executeSQL(String sql) throws Exception {
        Map<String, Object> result = new HashMap<>();
        long start = System.currentTimeMillis();

        // 使用原生连接（绕过 HikariCP），这样 stmt 就是 HiveStatement，能拿实时日志
        try (Connection conn = hiveConfig.getRawConnection();
             Statement stmt = conn.createStatement()) {

            String trimmed = sql.trim();
            // 去掉末尾分号
            if (trimmed.endsWith(";")) {
                trimmed = trimmed.substring(0, trimmed.length() - 1).trim();
            }

            // 跳过注释行和空行，找到第一条有效 SQL 语句来判断类型
            String firstStatement = null;
            String[] lines = trimmed.split("\\n");
            for (String line : lines) {
                String l = line.trim();
                if (!l.isEmpty() && !l.startsWith("--")) {
                    firstStatement = l;
                    break;
                }
            }

            // 全是注释或空行，直接返回成功
            if (firstStatement == null) {
                result.put("type", "execute");
                result.put("message", "无有效 SQL 语句");
                result.put("duration", System.currentTimeMillis() - start);
                result.put("success", true);
                result.put("hiveLogs", Collections.singletonList("[跳过] 仅包含注释或空行，无需执行"));
                return result;
            }

            String upper = firstStatement.toUpperCase();
            boolean isQuery = upper.startsWith("SELECT")
                    || upper.startsWith("SHOW")
                    || upper.startsWith("DESCRIBE")
                    || upper.startsWith("DESC")
                    || upper.startsWith("EXPLAIN")
                    || upper.startsWith("WITH");

            // 获取 HiveStatement 用于抓取执行日志
            HiveStatement hiveStmt = null;
            log.info("[Hive] Statement 实际类型: {}", stmt.getClass().getName());
            if (stmt instanceof HiveStatement) {
                hiveStmt = (HiveStatement) stmt;
                log.info("[Hive] 直接获取 HiveStatement 成功");
            } else {
                try {
                    if (stmt.isWrapperFor(HiveStatement.class)) {
                        hiveStmt = stmt.unwrap(HiveStatement.class);
                        log.info("[Hive] unwrap 获取 HiveStatement 成功");
                    }
                } catch (Exception ex) {
                    log.warn("[Hive] unwrap 失败: {}", ex.getMessage());
                }
            }
            if (hiveStmt == null) {
                log.warn("[Hive] 无法获取 HiveStatement，将无法获取实时执行日志");
            }

            // 开启 Hive CLI 打印头信息
            List<String> queryLogs = new ArrayList<>();
            queryLogs.add("[执行 SQL] " + trimmed.split("\\n")[0] + (trimmed.contains("\n") ? " ..." : ""));

            final String finalSql = trimmed;
            final Statement finalStmt = stmt;

            if (isQuery) {
                // 异步执行查询，主线程轮询日志
                final HiveStatement logStmt = hiveStmt;
                ExecutorService exec = Executors.newSingleThreadExecutor();
                Future<ResultSet> future = exec.submit(new Callable<ResultSet>() {
                    public ResultSet call() throws Exception { return finalStmt.executeQuery(finalSql); }
                });

                // 轮询 Hive 执行日志（MapReduce 进度等）
                pollHiveLogs(logStmt, queryLogs, future);

                try (ResultSet rs = future.get()) {
                    ResultSetMetaData meta = rs.getMetaData();
                    int colCount = meta.getColumnCount();
                    List<String> columns = new ArrayList<>();
                    for (int i = 1; i <= colCount; i++) columns.add(meta.getColumnLabel(i));

                    List<List<String>> rows = new ArrayList<>();
                    int rowLimit = Constants.MAX_QUERY_ROWS;
                    int count = 0;
                    while (rs.next() && count < rowLimit) {
                        List<String> row = new ArrayList<>();
                        for (int i = 1; i <= colCount; i++) {
                            String val = rs.getString(i);
                            row.add(val == null ? "NULL" : val);
                        }
                        rows.add(row);
                        count++;
                    }

                    long queryDuration = System.currentTimeMillis() - start;
                    queryLogs.add("[查询完成] 返回 " + count + " 行，" + colCount + " 列，耗时 " + queryDuration + "ms"
                            + (count >= rowLimit ? "（结果已截断，最多显示" + rowLimit + "行）" : ""));
                    result.put("type", "query");
                    result.put("columns", columns);
                    result.put("rows", rows);
                    result.put("rowCount", count);
                    result.put("truncated", count >= rowLimit);
                }
                exec.shutdown();
            } else {
                // 异步执行 DDL/DML，主线程轮询日志
                final HiveStatement logStmt = hiveStmt;
                ExecutorService exec = Executors.newSingleThreadExecutor();
                Future<Boolean> future = exec.submit(new Callable<Boolean>() {
                    public Boolean call() throws Exception { return finalStmt.execute(finalSql); }
                });

                pollHiveLogs(logStmt, queryLogs, future);
                future.get(); // 等待完成

                long execDuration = System.currentTimeMillis() - start;
                queryLogs.add("[执行完成] 语句执行成功，耗时 " + execDuration + "ms");
                result.put("type", "execute");
                result.put("message", "执行成功");
                exec.shutdown();
            }
            result.put("hiveLogs", queryLogs);

            long duration = System.currentTimeMillis() - start;
            result.put("duration", duration);
            result.put("success", true);

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - start;
            List<String> errLogs = new ArrayList<>();
            errLogs.add("[错误] " + e.getMessage());
            result.put("hiveLogs", errLogs);
            result.put("success", false);
            result.put("duration", duration);
            result.put("error", e.getMessage());
            throw e;
        }

        return result;
    }

    /**
     * 生成 ODS 表名
     */
    private static final java.util.regex.Pattern SAFE_NAME = java.util.regex.Pattern.compile("[a-zA-Z0-9_]+");

    public String getOdsTableName(String sourceDb, String sourceTable, String syncMode) {
        validateName(sourceDb, "数据库名");
        validateName(sourceTable, "表名");
        String suffix = "df";
        if (syncMode != null && (syncMode.equals("di") || syncMode.equals("incr"))) {
            suffix = "di";
        }
        return "ods_" + sourceDb + "_" + sourceTable.toLowerCase() + "_" + suffix;
    }

    private void validateName(String name, String label) {
        if (name == null || name.isEmpty() || !SAFE_NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("非法的" + label + ": " + name);
        }
    }

    /** 日志回调接口 */
    public interface LogCallback {
        void onLog(String level, String message);
        void onResult(Map<String, Object> result);
        void onError(String error);
    }

    /**
     * 流式执行 SQL — 实时回调日志
     */
    public void executeSQLWithStream(String sql, LogCallback callback) {
        try (Connection conn = hiveConfig.getRawConnection();
             Statement stmt = conn.createStatement()) {

            // 按分号拆分
            String[] parts = sql.split(";");
            List<String> validStmts = new ArrayList<>();
            for (String s : parts) {
                String c = s.replaceAll("--[^\\n]*", "").trim();
                if (!c.isEmpty()) validStmts.add(s.trim());
            }

            if (validStmts.isEmpty()) {
                callback.onError("没有可执行的 SQL 语句");
                return;
            }

            HiveStatement hiveStmt = null;
            if (stmt instanceof HiveStatement) {
                hiveStmt = (HiveStatement) stmt;
            }

            Map<String, Object> lastResult = new HashMap<>();
            int total = validStmts.size();

            for (int i = 0; i < total; i++) {
                String stmtSql = validStmts.get(i);
                if (stmtSql.endsWith(";")) stmtSql = stmtSql.substring(0, stmtSql.length() - 1).trim();
                final String finalSql = stmtSql;

                String preview = stmtSql.length() > 100 ? stmtSql.substring(0, 100) + "..." : stmtSql;
                callback.onLog("INFO", "[" + (i+1) + "/" + total + "] 执行: " + preview);

                long start = System.currentTimeMillis();

                // 判断类型
                String firstLine = stmtSql;
                for (String line : stmtSql.split("\\n")) {
                    String l = line.trim();
                    if (!l.isEmpty() && !l.startsWith("--")) { firstLine = l; break; }
                }
                String upper = firstLine.toUpperCase();
                boolean isQuery = upper.startsWith("SELECT") || upper.startsWith("SHOW")
                    || upper.startsWith("DESCRIBE") || upper.startsWith("WITH");

                // 异步执行，主线程轮询日志
                final Statement fStmt = stmt;
                ExecutorService exec = Executors.newSingleThreadExecutor();
                Future<?> future;

                if (isQuery) {
                    future = exec.submit(new Callable<ResultSet>() {
                        public ResultSet call() throws Exception { return fStmt.executeQuery(finalSql); }
                    });
                } else {
                    future = exec.submit(new Callable<Boolean>() {
                        public Boolean call() throws Exception { return fStmt.execute(finalSql); }
                    });
                }

                // 实时轮询 Hive 日志
                if (hiveStmt != null) {
                    while (!future.isDone()) {
                        try {
                            List<String> logs = hiveStmt.getQueryLog(true, 100);
                            if (logs != null) {
                                for (String line : logs) {
                                    if (line != null && !line.trim().isEmpty()) {
                                        callback.onLog("HIVE", line);
                                    }
                                }
                            }
                        } catch (Exception ignored) {}
                        Thread.sleep(300);
                    }
                    // 最后再拉一次
                    try {
                        List<String> logs = hiveStmt.getQueryLog(true, 1000);
                        if (logs != null) {
                            for (String line : logs) {
                                if (line != null && !line.trim().isEmpty()) {
                                    callback.onLog("HIVE", line);
                                }
                            }
                        }
                    } catch (Exception ignored) {}
                }

                // 获取结果
                try {
                    Object res = future.get();
                    long dur = System.currentTimeMillis() - start;

                    if (isQuery && res instanceof ResultSet) {
                        ResultSet rs = (ResultSet) res;
                        ResultSetMetaData meta = rs.getMetaData();
                        int colCount = meta.getColumnCount();
                        List<String> columns = new ArrayList<>();
                        for (int c = 1; c <= colCount; c++) columns.add(meta.getColumnLabel(c));

                        List<List<String>> rows = new ArrayList<>();
                        int count = 0;
                        while (rs.next() && count < Constants.MAX_QUERY_ROWS) {
                            List<String> row = new ArrayList<>();
                            for (int c = 1; c <= colCount; c++) {
                                String val = rs.getString(c);
                                row.add(val == null ? "NULL" : val);
                            }
                            rows.add(row);
                            count++;
                        }
                        rs.close();

                        lastResult.put("type", "query");
                        lastResult.put("columns", columns);
                        lastResult.put("rows", rows);
                        lastResult.put("rowCount", count);
                        lastResult.put("duration", dur);
                        lastResult.put("success", true);
                        callback.onLog("OK", "[" + (i+1) + "/" + total + "] 查询完成，返回 " + count + " 行，耗时 " + dur + "ms");
                    } else {
                        lastResult.put("type", "execute");
                        lastResult.put("duration", dur);
                        lastResult.put("success", true);
                        callback.onLog("OK", "[" + (i+1) + "/" + total + "] 执行完成，耗时 " + dur + "ms");
                    }
                } catch (java.util.concurrent.ExecutionException ee) {
                    String err = ee.getCause() != null ? ee.getCause().getMessage() : ee.getMessage();
                    callback.onLog("ERROR", "[" + (i+1) + "/" + total + "] 失败: " + err);
                    callback.onError("第 " + (i+1) + " 条语句失败: " + err);
                    exec.shutdown();
                    return;
                }
                exec.shutdown();
            }

            callback.onLog("OK", "全部 " + total + " 条语句执行完成");
            callback.onResult(lastResult);

        } catch (Exception e) {
            callback.onError(e.getMessage());
        }
    }

    /**
     * 轮询 Hive 执行日志（MapReduce 进度等），通过 WebSocket 实时推送
     */
    private void pollHiveLogs(HiveStatement hiveStmt, List<String> queryLogs, Future<?> future) {
        if (hiveStmt == null) return;
        try {
            while (!future.isDone()) {
                try {
                    List<String> logs = hiveStmt.getQueryLog(true, 100);
                    if (logs != null) {
                        for (String line : logs) {
                            if (line != null && !line.trim().isEmpty()) {
                                queryLogs.add(line);
                                pushLog(line);
                            }
                        }
                    }
                } catch (Exception e) {
                    // getQueryLog 在某些状态下可能抛异常，忽略继续轮询
                }
                Thread.sleep(500); // 每 500ms 轮询一次
            }
            // 执行完后再拉一次，确保不遗漏
            try {
                List<String> logs = hiveStmt.getQueryLog(true, 1000);
                if (logs != null) {
                    for (String line : logs) {
                        if (line != null && !line.trim().isEmpty()) {
                            queryLogs.add(line);
                            pushLog(line);
                        }
                    }
                }
            } catch (Exception ignored) {}
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void pushLog(String message) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("level", "HIVE");
            payload.put("message", message);
            payload.put("time", java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss")));
            messagingTemplate.convertAndSend("/topic/sql-log", payload);
        } catch (Exception ignored) {}
    }
}
