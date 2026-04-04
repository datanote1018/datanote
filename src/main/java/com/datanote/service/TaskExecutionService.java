package com.datanote.service;

import com.datanote.common.Constants;
import com.datanote.mapper.*;
import com.datanote.model.*;
import com.datanote.util.ProcessUtil;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;

/**
 * 任务执行服务 — 负责脚本/同步任务的实际执行、超时控制、失败处理和重试
 */
@Service
@RequiredArgsConstructor
public class TaskExecutionService {

    private static final Logger log = LoggerFactory.getLogger(TaskExecutionService.class);

    private final DnScriptMapper scriptMapper;
    private final DnSyncTaskMapper syncTaskMapper;
    private final DnSchedulerRunMapper runMapper;
    private final DnDatasourceMapper datasourceMapper;
    private final DnTaskExecutionMapper taskExecutionMapper;
    private final HiveService hiveService;
    private final DataxService dataxService;
    private final MetadataService metadataService;
    private final TaskDependencyService taskDependencyService;
    private final LogBroadcastService logBroadcastService;
    private final TaskSchedulerService taskSchedulerService;

    @Value("${datax.job-dir}")
    private String jobDir;

    // 指数退避参数
    private static final long RETRY_BASE_MS = 5000;    // 5 秒
    private static final long RETRY_MAX_MS = 300000;   // 5 分钟

    // 引用全局常量
    private static final int MAX_LOG_SIZE = Constants.MAX_LOG_SIZE;

    // ======================== 带超时的任务执行 ========================

    /**
     * 带超时的任务执行
     */
    public void executeTaskWithTimeout(DnSchedulerRun run) {
        // 获取超时配置
        int timeoutSeconds = getTaskTimeout(run);

        log.info("开始执行任务: {} {} (runDate={}, timeout={}s)",
                run.getTaskType(), run.getTaskId(), run.getRunDate(), timeoutSeconds);

        logBroadcastService.broadcastTaskLog(run.getTaskId(), run.getTaskType(), "INFO",
                "开始执行任务 (timeout=" + timeoutSeconds + "s)");

        // 更新状态为 RUNNING
        run.setStatus(DnSchedulerRun.STATUS_RUNNING);
        run.setStartTime(LocalDateTime.now());
        runMapper.updateById(run);
        logBroadcastService.broadcastStatusChange(run.getTaskId(), run.getTaskType(),
                DnSchedulerRun.STATUS_RUNNING, run.getRunDate().toString());

        StringBuilder logBuilder = new StringBuilder();
        String bizdate = run.getRunDate().format(DateTimeFormatter.ISO_LOCAL_DATE);

        // 使用 Future 实现超时控制（复用共享线程池）
        ExecutorService timeoutExecutor = taskSchedulerService.getTimeoutExecutor();
        Future<?> future = timeoutExecutor.submit(() -> {
            try {
                if (Constants.TASK_TYPE_SYNC_TASK.equals(run.getTaskType())) {
                    executeSyncTask(run.getTaskId(), bizdate, logBuilder);
                } else {
                    executeScript(run.getTaskId(), bizdate, logBuilder);
                }
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        });

        try {
            future.get(timeoutSeconds, TimeUnit.SECONDS);

            // 执行成功
            run.setStatus(DnSchedulerRun.STATUS_SUCCESS);
            run.setEndTime(LocalDateTime.now());
            run.setLog(truncateLog(logBuilder));
            runMapper.updateById(run);
            log.info("任务执行成功: {} {} (runDate={})", run.getTaskType(), run.getTaskId(), run.getRunDate());

            // 落盘结构化执行指标
            saveTaskExecution(run, "SUCCESS", logBuilder.toString());
            logBroadcastService.broadcastTaskLog(run.getTaskId(), run.getTaskType(), "INFO", "任务执行成功");
            logBroadcastService.broadcastStatusChange(run.getTaskId(), run.getTaskType(),
                    DnSchedulerRun.STATUS_SUCCESS, run.getRunDate().toString());

            // 恢复被暂停的下游任务（设为 WAITING，让依赖检查决定是否执行）
            taskDependencyService.resumeDownstreamAfterSuccess(
                    run.getTaskId(), run.getTaskType(), run.getRunDate(), run.getRunType());

            // 触发下游检查
            taskSchedulerService.processWaitingTasks(run.getRunDate(), run.getRunType());

        } catch (TimeoutException e) {
            future.cancel(true);
            logBuilder.append("\n[TIMEOUT] 任务执行超时（").append(timeoutSeconds).append("秒），已强制终止");
            handleTaskFailure(run, logBuilder, "任务执行超时");

        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            logBuilder.append("\n[ERROR] ").append(cause != null ? cause.getMessage() : e.getMessage());
            handleTaskFailure(run, logBuilder, cause != null ? cause.getMessage() : e.getMessage());

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logBuilder.append("\n[INTERRUPTED] 任务被中断");
            handleTaskFailure(run, logBuilder, "任务被中断");

        } finally {
            // 共享线程池不需要 shutdown
        }
    }

    // ======================== 失败处理与重试 ========================

    /**
     * 处理任务失败：记录状态，判断是否自动重试
     */
    private void handleTaskFailure(DnSchedulerRun run, StringBuilder logBuilder, String errorMsg) {
        log.error("任务执行失败: {} {} - {}", run.getTaskType(), run.getTaskId(), errorMsg);

        int retryCount = run.getRetryCount() != null ? run.getRetryCount() : 0;
        int maxRetries = getTaskMaxRetries(run);

        if (retryCount < maxRetries) {
            // 自动重试（指数退避）
            long delayMs = calculateRetryDelay(retryCount);
            logBuilder.append("\n[RETRY] 第 ").append(retryCount + 1).append("/").append(maxRetries)
                      .append(" 次重试，").append(delayMs / 1000).append("秒后执行");

            run.setStatus(DnSchedulerRun.STATUS_WAITING);
            run.setRetryCount(retryCount + 1);
            run.setEndTime(LocalDateTime.now());
            run.setLog(truncateLog(logBuilder));
            runMapper.updateById(run);

            // 延迟后重新触发
            ExecutorService executor = taskSchedulerService.getExecutor();
            executor.submit(() -> {
                try {
                    Thread.sleep(delayMs);
                    taskSchedulerService.processWaitingTasks(run.getRunDate(), run.getRunType());
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            });
        } else {
            // 达到最大重试次数，标记为失败
            if (maxRetries > 0) {
                logBuilder.append("\n[FAILED] 已达到最大重试次数（").append(maxRetries).append("），任务失败");
            }
            run.setStatus(DnSchedulerRun.STATUS_FAILED);
            run.setEndTime(LocalDateTime.now());
            run.setLog(truncateLog(logBuilder));
            runMapper.updateById(run);

            // 落盘结构化执行指标
            saveTaskExecution(run, "FAILED", logBuilder.toString());

            // 自动暂停所有下游任务
            int paused = taskDependencyService.pauseDownstream(run.getTaskId(), run.getTaskType(),
                    run.getRunDate(), run.getRunType());
            if (paused > 0) {
                log.info("任务 {}:{} 失败，已自动暂停 {} 个下游任务",
                        run.getTaskType(), run.getTaskId(), paused);
            }
        }
    }

    // ======================== 具体执行逻辑 ========================

    private void executeSyncTask(Long taskId, String bizdate, StringBuilder logBuilder) throws Exception {
        DnSyncTask task = syncTaskMapper.selectById(taskId);
        if (task == null) throw new RuntimeException("同步任务不存在: " + taskId);

        logBuilder.append("[").append(nowTime()).append("] 开始执行同步任务: ").append(task.getTaskName()).append("\n");
        logBuilder.append("源表: ").append(task.getSourceDb()).append(".").append(task.getSourceTable()).append("\n");
        logBuilder.append("目标表: ods.").append(task.getTargetTable()).append("\n");
        logBuilder.append("数据日期: ").append(bizdate).append("\n\n");

        DnDatasource ds = datasourceMapper.selectById(task.getSourceDsId());
        if (ds == null) throw new RuntimeException("数据源不存在: " + task.getSourceDsId());
        String syncMode = task.getSyncMode() != null ? task.getSyncMode() : "df";
        String targetTable = "ods." + task.getTargetTable();

        // ========== 第一步：预检（任一失败直接中止） ==========

        // 1.1 检查 MySQL 源库连通性
        logBuilder.append("[预检] MySQL 源库连接...");
        try {
            metadataService.getColumns(task.getSourceDb(), task.getSourceTable());
            logBuilder.append(" 正常\n");
        } catch (Exception e) {
            logBuilder.append(" 失败: ").append(e.getMessage()).append("\n");
            throw new RuntimeException("MySQL 源库连接失败: " + e.getMessage());
        }

        // 1.2 确保 Hive 表和分区存在
        logBuilder.append("[预检] Hive 表和分区...");
        try {
            List<com.datanote.model.ColumnInfo> cols = metadataService.getColumns(task.getSourceDb(), task.getSourceTable());
            String ddl = hiveService.generateDDL(task.getSourceDb(), task.getSourceTable(), cols, syncMode);
            hiveService.executeDDL(ddl);
            hiveService.executeDDL("ALTER TABLE " + targetTable + " ADD IF NOT EXISTS PARTITION (dt='" + bizdate + "')");
            logBuilder.append(" 就绪\n");
        } catch (Exception e) {
            logBuilder.append(" 失败: ").append(e.getMessage()).append("\n");
            throw new RuntimeException("Hive 表/分区创建失败: " + e.getMessage());
        }

        // ========== 第二步：生成 DataX 配置（每次动态生成，确保日期和列是最新的） ==========

        logBuilder.append("[DataX] 生成配置...\n");
        List<com.datanote.model.ColumnInfo> columns = metadataService.getColumns(task.getSourceDb(), task.getSourceTable());
        String jobFile = dataxService.generateJobJson(
                ds.getHost(), ds.getPort(), ds.getUsername(), ds.getPassword(),
                task.getSourceDb(), task.getSourceTable(), task.getTargetTable(), columns);
        logBuilder.append("[DataX] 配置生成完成: ").append(jobFile).append("\n");

        // ========== 第三步：执行 DataX ==========

        logBuilder.append("[DataX] 开始同步数据...\n");
        ProcessUtil.ExecResult result = dataxService.runJob(jobFile);
        logBuilder.append(result.getOutput());

        // 执行完删除含密码的临时文件
        try { new java.io.File(jobFile).delete(); } catch (Exception ignored) {}

        if (result.getExitCode() != 0) {
            throw new RuntimeException("DataX 执行失败，退出码: " + result.getExitCode());
        }

        // ========== 第四步：后处理（失败不影响整体结果） ==========

        // 4.1 修复分区
        logBuilder.append("\n[后处理] MSCK REPAIR TABLE...");
        try {
            hiveService.executeDDL("MSCK REPAIR TABLE " + targetTable);
            logBuilder.append(" 成功\n");
        } catch (Exception e) {
            logBuilder.append(" 跳过(").append(e.getMessage()).append(")\n");
        }

        // 4.2 更新统计
        logBuilder.append("[后处理] ANALYZE TABLE...");
        try {
            hiveService.executeDDL("ANALYZE TABLE " + targetTable + " PARTITION (dt) COMPUTE STATISTICS");
            logBuilder.append(" 成功\n");
        } catch (Exception e) {
            logBuilder.append(" 跳过(").append(e.getMessage()).append(")\n");
        }

        logBuilder.append("\n[完成] 耗时: ").append(result.getDurationMs() / 1000).append("秒\n");
    }

    private void executeScript(Long scriptId, String bizdate, StringBuilder logBuilder) throws Exception {
        DnScript script = scriptMapper.selectById(scriptId);
        if (script == null) throw new RuntimeException("脚本不存在: " + scriptId);

        logBuilder.append("[").append(nowTime()).append("] 开始执行脚本: ").append(script.getScriptName()).append("\n");
        logBuilder.append("脚本类型: ").append(script.getScriptType()).append("\n");
        logBuilder.append("数据日期: ").append(bizdate).append("\n\n");

        String content = script.getContent();
        if (content == null || content.trim().isEmpty()) {
            throw new RuntimeException("脚本内容为空");
        }

        String executeSql = content.replace("${bizdate}", bizdate);
        logBuilder.append("[参数替换] ${bizdate} -> ").append(bizdate).append("\n\n");

        String scriptType = script.getScriptType() != null ? script.getScriptType().toLowerCase() : "hive";

        if ("shell".equals(scriptType)) {
            String[] cmd = {"/bin/bash", "-c", executeSql};
            int timeout = script.getTimeoutSeconds() != null ? script.getTimeoutSeconds() : 3600;
            ProcessUtil.ExecResult result = ProcessUtil.exec(cmd, timeout);
            logBuilder.append(result.getOutput());
            if (result.getExitCode() != 0) {
                throw new RuntimeException("Shell 执行失败，退出码: " + result.getExitCode());
            }
        } else {
            String[] statements = splitSQL(executeSql);
            for (int i = 0; i < statements.length; i++) {
                String stmt = statements[i].trim();
                if (stmt.isEmpty()) continue;

                logBuilder.append("[执行第 ").append(i + 1).append("/").append(statements.length).append(" 条语句]\n");
                Map<String, Object> result = hiveService.executeSQL(stmt);
                Boolean success = (Boolean) result.get("success");
                if (success == null || !success) {
                    throw new RuntimeException("HiveSQL 执行失败: " + result.get("error"));
                }

                @SuppressWarnings("unchecked")
                List<String> hiveLogs = (List<String>) result.get("hiveLogs");
                if (hiveLogs != null) {
                    for (String hiveLog : hiveLogs) {
                        logBuilder.append(hiveLog).append("\n");
                    }
                }
                logBuilder.append("[语句 ").append(i + 1).append(" 完成] 耗时 ")
                          .append(result.get("duration")).append("ms\n\n");
            }
        }
        logBuilder.append("[全部完成]\n");
    }

    // ======================== 手动重试 ========================

    /**
     * 手动重试失败或暂停的任务
     *
     * @param runId 运行记录 ID
     */
    public void retryTask(Long runId) {
        DnSchedulerRun run = runMapper.selectById(runId);
        if (run == null) throw new RuntimeException("运行记录不存在");
        if (run.getStatus() != DnSchedulerRun.STATUS_FAILED &&
            run.getStatus() != DnSchedulerRun.STATUS_PAUSED) {
            throw new RuntimeException("只能重试失败或暂停的任务，当前状态: " + run.getStatus());
        }

        run.setStatus(DnSchedulerRun.STATUS_WAITING);
        run.setRetryCount(0); // 手动重试重置计数
        run.setLog(null);
        run.setStartTime(null);
        run.setEndTime(null);
        runMapper.updateById(run);

        taskSchedulerService.setEnabled(true);
        taskSchedulerService.processWaitingTasks(run.getRunDate(), run.getRunType());
    }

    // ======================== 工具方法 ========================

    /**
     * 指数退避 + Full Jitter
     */
    private long calculateRetryDelay(int attempt) {
        long exponential = Math.min(RETRY_MAX_MS, RETRY_BASE_MS * (1L << attempt));
        return ThreadLocalRandom.current().nextLong(RETRY_BASE_MS, exponential + 1);
    }

    private int getTaskTimeout(DnSchedulerRun run) {
        if (Constants.TASK_TYPE_SCRIPT.equals(run.getTaskType())) {
            DnScript s = scriptMapper.selectById(run.getTaskId());
            return (s != null && s.getTimeoutSeconds() != null && s.getTimeoutSeconds() > 0)
                    ? s.getTimeoutSeconds() : 7200; // 默认 2 小时
        } else {
            DnSyncTask t = syncTaskMapper.selectById(run.getTaskId());
            return (t != null && t.getTimeoutSeconds() != null && t.getTimeoutSeconds() > 0)
                    ? t.getTimeoutSeconds() : 3600; // 默认 1 小时
        }
    }

    private int getTaskMaxRetries(DnSchedulerRun run) {
        if (Constants.TASK_TYPE_SCRIPT.equals(run.getTaskType())) {
            DnScript s = scriptMapper.selectById(run.getTaskId());
            return (s != null && s.getRetryTimes() != null) ? s.getRetryTimes() : 0;
        } else {
            DnSyncTask t = syncTaskMapper.selectById(run.getTaskId());
            return (t != null && t.getRetryTimes() != null) ? t.getRetryTimes() : 0;
        }
    }

    private String truncateLog(StringBuilder logBuilder) {
        if (logBuilder.length() > MAX_LOG_SIZE) {
            logBuilder.setLength(MAX_LOG_SIZE);
            logBuilder.append("\n\n[日志已截断，超过 1MB 限制]");
        }
        return logBuilder.toString();
    }

    private String nowTime() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
    }

    private String[] splitSQL(String sql) {
        return java.util.Arrays.stream(sql.split(";"))
                .map(String::trim)
                .filter(s -> !s.isEmpty() && !s.startsWith("--"))
                .toArray(String[]::new);
    }

    // ======================== 结构化执行指标落盘 ========================

    private void saveTaskExecution(DnSchedulerRun run, String status, String logText) {
        try {
            DnTaskExecution exec = new DnTaskExecution();
            if (Constants.TASK_TYPE_SCRIPT.equals(run.getTaskType())) {
                exec.setScriptId(run.getTaskId());
            } else {
                exec.setSyncTaskId(run.getTaskId());
            }
            exec.setTaskType(run.getTaskType());
            exec.setTriggerType(run.getRunType());
            exec.setStatus(status);
            exec.setStartTime(run.getStartTime());
            exec.setEndTime(run.getEndTime());
            exec.setExecutor("local");
            exec.setCreatedAt(LocalDateTime.now());

            // 计算耗时
            if (run.getStartTime() != null && run.getEndTime() != null) {
                exec.setDuration((int) java.time.Duration.between(run.getStartTime(), run.getEndTime()).getSeconds());
            }

            // 从 DataX 日志中解析读写行数
            if (Constants.TASK_TYPE_SYNC_TASK.equals(run.getTaskType()) && logText != null) {
                exec.setReadCount(parseCountFromLog(logText, "读出记录总数"));
                exec.setWriteCount(parseCountFromLog(logText, "写出记录总数"));
                long readErr = parseCountFromLog(logText, "读写失败总数");
                if (readErr <= 0) readErr = parseCountFromLog(logText, "脏数据");
                exec.setErrorCount(readErr);
            }

            taskExecutionMapper.insert(exec);
        } catch (Exception e) {
            log.warn("保存执行指标失败: {}", e.getMessage());
        }
    }

    /**
     * 从 DataX 日志中解析数值（如 "读出记录总数 : 12345"）
     */
    private long parseCountFromLog(String logText, String keyword) {
        int idx = logText.indexOf(keyword);
        if (idx < 0) return 0;
        String after = logText.substring(idx + keyword.length());
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\s*[:：]?\\s*(\\d+)").matcher(after);
        return m.find() ? Long.parseLong(m.group(1)) : 0;
    }
}
