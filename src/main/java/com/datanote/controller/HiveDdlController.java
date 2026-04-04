package com.datanote.controller;

import com.datanote.exception.BusinessException;
import com.datanote.mapper.DnTaskExecutionMapper;
import com.datanote.model.ColumnInfo;
import com.datanote.model.DnTaskExecution;
import com.datanote.model.R;
import com.datanote.model.dto.HiveCreateTableRequest;
import com.datanote.model.dto.HiveExecuteRequest;
import com.datanote.service.HiveService;
import com.datanote.service.LogBroadcastService;
import com.datanote.service.MetadataService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Hive DDL 管理 Controller
 */
@Slf4j
@RestController
@RequestMapping("/api/hive")
@RequiredArgsConstructor
@Tag(name = "Hive DDL", description = "Hive 建表语句预览与执行")
public class HiveDdlController {

    private final HiveService hiveService;
    private final MetadataService metadataService;
    private final SimpMessagingTemplate messagingTemplate;
    private final DnTaskExecutionMapper taskExecutionMapper;

    /**
     * 预览 Hive DDL（不执行）
     */
    @Operation(summary = "预览建表 DDL")
    @GetMapping("/preview-ddl")
    public R<Map<String, String>> previewDDL(@RequestParam String db, @RequestParam String table,
                                              @RequestParam(required = false, defaultValue = "df") String syncMode) {
        try {
            List<ColumnInfo> columns = metadataService.getColumns(db, table);
            String ddl = hiveService.generateDDL(db, table, columns, syncMode);
            String odsTable = hiveService.getOdsTableName(db, table, syncMode);

            Map<String, String> result = new HashMap<>();
            result.put("ddl", ddl);
            result.put("odsTable", odsTable);
            return R.ok(result);
        } catch (Exception e) {
            log.error("预览 Hive DDL 失败, db={}, table={}", db, table, e);
            return R.fail("预览建表语句失败");
        }
    }

    /**
     * 在线执行 HiveSQL
     */
    @Operation(summary = "执行 HiveSQL")
    @PostMapping("/execute")
    public R<Map<String, Object>> executeSQL(@RequestBody HiveExecuteRequest body) {
        try {
            String sql = body.getSql();
            if (sql == null || sql.trim().isEmpty()) {
                throw new BusinessException("SQL 不能为空");
            }

            // 按分号拆分多条语句，逐条执行，实时推送日志
            String[] statements = sql.split(";");
            // 预先统计有效语句数
            java.util.List<String> validStmts = new java.util.ArrayList<>();
            for (String s : statements) {
                String c = s.replaceAll("--[^\\n]*", "").trim();
                if (!c.isEmpty()) validStmts.add(s.trim());
            }
            int totalStmts = validStmts.size();
            if (totalStmts == 0) throw new BusinessException("没有可执行的 SQL 语句");

            Map<String, Object> lastResult = null;
            int executed = 0;
            List<String> allLogs = new java.util.ArrayList<>();

            for (int i = 0; i < totalStmts; i++) {
                String stmt = validStmts.get(i);
                // 实时推送：开始执行第 N 条
                String preview = stmt.length() > 80 ? stmt.substring(0, 80) + "..." : stmt;
                pushSqlLog("INFO", "执行第 " + (i + 1) + "/" + totalStmts + " 条语句: " + preview);

                long start = System.currentTimeMillis();
                Map<String, Object> result = hiveService.executeSQL(stmt);
                long dur = System.currentTimeMillis() - start;
                Boolean success = (Boolean) result.get("success");

                // 实时推送 Hive 日志
                @SuppressWarnings("unchecked")
                List<String> logs = (List<String>) result.get("hiveLogs");
                if (logs != null) {
                    for (String line : logs) pushSqlLog("HIVE", line);
                    allLogs.addAll(logs);
                }

                if (success == null || !success) {
                    pushSqlLog("ERROR", "第 " + (i + 1) + " 条语句失败: " + result.get("error"));
                    result.put("failedAt", "第 " + (i + 1) + "/" + totalStmts + " 条语句");
                    result.put("hiveLogs", allLogs);
                    R<Map<String, Object>> failResp = R.ok(result);
                    failResp.setCode(-1);
                    failResp.setMsg("SQL 执行失败：" + (result.get("error") != null ? result.get("error") : "未知错误"));
                    return failResp;
                }

                pushSqlLog("OK", "第 " + (i + 1) + " 条语句完成，耗时 " + dur + "ms");
                lastResult = result;
                executed++;
            }

            pushSqlLog("OK", "全部 " + executed + " 条语句执行完成");
            lastResult.put("hiveLogs", allLogs);
            lastResult.put("executedCount", executed);
            return R.ok(lastResult);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("执行 HiveSQL 失败", e);
            // 把异常信息塞进 data 返回给前端
            Map<String, Object> errData = new java.util.HashMap<>();
            errData.put("error", e.getMessage());
            // 提取根因
            Throwable cause = e;
            while (cause.getCause() != null) cause = cause.getCause();
            if (cause != e) errData.put("rootCause", cause.getMessage());
            R<Map<String, Object>> resp = R.ok(errData);
            resp.setCode(-1);
            resp.setMsg("执行 SQL 失败");
            return resp;
        }
    }

    // 存储待执行的 SQL（POST 提交 → GET SSE 订阅）
    private final java.util.concurrent.ConcurrentHashMap<Long, String> pendingSql = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * 提交 SQL 执行请求（POST），返回 executionId，前端用 executionId 订阅 SSE
     */
    @PostMapping("/submit-execute")
    public R<Map<String, Object>> submitExecute(@RequestBody Map<String, Object> body) {
        String sql = (String) body.get("sql");
        Long scriptId = body.get("scriptId") != null ? Long.valueOf(body.get("scriptId").toString()) : null;
        Long syncTaskId = body.get("syncTaskId") != null ? Long.valueOf(body.get("syncTaskId").toString()) : null;

        DnTaskExecution exec = new DnTaskExecution();
        exec.setScriptId(scriptId);
        exec.setSyncTaskId(syncTaskId);
        exec.setTaskType(scriptId != null ? "script" : syncTaskId != null ? "syncTask" : "manual");
        exec.setTriggerType("manual");
        exec.setStatus("RUNNING");
        exec.setStartTime(java.time.LocalDateTime.now());
        taskExecutionMapper.insert(exec);

        pendingSql.put(exec.getId(), sql);

        Map<String, Object> result = new HashMap<>();
        result.put("executionId", exec.getId());
        return R.ok(result);
    }

    /**
     * SSE 订阅执行日志（GET，只传 executionId，不传 SQL）
     */
    @GetMapping("/stream-execute")
    public SseEmitter streamExecute(@RequestParam(required = false) String sql,
                                     @RequestParam(required = false) Long executionId,
                                     @RequestParam(required = false) Long scriptId,
                                     @RequestParam(required = false) Long syncTaskId) {
        SseEmitter emitter = new SseEmitter(600000L);

        // 兼容两种模式：旧的直接传 sql，新的传 executionId
        final String execSql;
        final Long execId;
        if (executionId != null && pendingSql.containsKey(executionId)) {
            execSql = pendingSql.remove(executionId);
            execId = executionId;
        } else if (sql != null && !sql.isEmpty()) {
            // 旧模式兼容：短 SQL 直接传
            DnTaskExecution exec = new DnTaskExecution();
            exec.setScriptId(scriptId);
            exec.setSyncTaskId(syncTaskId);
            exec.setTaskType(scriptId != null ? "script" : syncTaskId != null ? "syncTask" : "manual");
            exec.setTriggerType("manual");
            exec.setStatus("RUNNING");
            exec.setStartTime(java.time.LocalDateTime.now());
            taskExecutionMapper.insert(exec);
            execId = exec.getId();
            execSql = sql;
        } else {
            try { emitter.send(SseEmitter.event().name("error").data(
                    java.util.Collections.singletonMap("message", "没有可执行的 SQL"))); emitter.complete(); } catch (Exception ignored) {}
            return emitter;
        }

        final StringBuilder logBuffer = new StringBuilder();
        final long startMs = System.currentTimeMillis();

        new Thread(new Runnable() {
            public void run() {
                try {
                    hiveService.executeSQLWithStream(execSql, new HiveService.LogCallback() {
                        public void onLog(String level, String message) {
                            try {
                                logBuffer.append("[").append(level).append("] ").append(message).append("\n");
                                Map<String, Object> data = new HashMap<>();
                                data.put("level", level);
                                data.put("message", message);
                                data.put("time", java.time.LocalDateTime.now().format(
                                    java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss")));
                                emitter.send(SseEmitter.event().data(data));
                            } catch (Exception ignored) {}
                        }
                        public void onResult(Map<String, Object> result) {
                            try {
                                // 更新执行记录为成功
                                updateExecution(execId, "SUCCESS", logBuffer.toString(), startMs);
                                emitter.send(SseEmitter.event().name("result").data(result));
                                emitter.complete();
                            } catch (Exception ignored) {}
                        }
                        public void onError(String error) {
                            try {
                                logBuffer.append("[ERROR] ").append(error).append("\n");
                                updateExecution(execId, "FAILED", logBuffer.toString(), startMs);
                                Map<String, Object> data = new HashMap<>();
                                data.put("level", "ERROR");
                                data.put("message", error);
                                emitter.send(SseEmitter.event().name("error").data(data));
                                emitter.complete();
                            } catch (Exception ignored) {}
                        }
                    });
                } catch (Exception e) {
                    try {
                        logBuffer.append("[ERROR] ").append(e.getMessage()).append("\n");
                        updateExecution(execId, "FAILED", logBuffer.toString(), startMs);
                        emitter.send(SseEmitter.event().name("error").data(
                            java.util.Collections.singletonMap("message", e.getMessage())));
                        emitter.complete();
                    } catch (Exception ignored) {}
                }
            }
        }).start();

        return emitter;
    }

    private void updateExecution(Long execId, String status, String log, long startMs) {
        DnTaskExecution update = new DnTaskExecution();
        update.setId(execId);
        update.setStatus(status);
        update.setEndTime(java.time.LocalDateTime.now());
        update.setDuration((int)((System.currentTimeMillis() - startMs) / 1000));
        update.setLog(log.length() > 50000 ? log.substring(log.length() - 50000) : log);
        taskExecutionMapper.updateById(update);
    }

    /** 通过 WebSocket 实时推送 SQL 执行日志 */
    private void pushSqlLog(String level, String message) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("level", level);
        payload.put("message", message);
        payload.put("time", java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss")));
        messagingTemplate.convertAndSend("/topic/sql-log", payload);
    }

    /**
     * 执行 Hive 建表
     */
    @Operation(summary = "创建 Hive 表")
    @PostMapping("/create-table")
    public R<Map<String, String>> createTable(@RequestBody HiveCreateTableRequest body) {
        try {
            String db = body.getDb();
            String table = body.getTable();
            String syncMode = body.getSyncMode() != null ? body.getSyncMode() : "df";

            List<ColumnInfo> columns = metadataService.getColumns(db, table);
            String ddl = hiveService.generateDDL(db, table, columns, syncMode);
            hiveService.executeDDL(ddl);

            String odsTable = hiveService.getOdsTableName(db, table, syncMode);
            Map<String, String> result = new HashMap<>();
            result.put("odsTable", odsTable);
            result.put("ddl", ddl);
            return R.ok(result);
        } catch (Exception e) {
            log.error("创建 Hive 表失败, db={}, table={}", body.getDb(), body.getTable(), e);
            return R.fail("创建 Hive 表失败");
        }
    }
}
