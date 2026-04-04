package com.datanote.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.datanote.common.Constants;
import com.datanote.mapper.*;
import com.datanote.model.*;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * 本地任务调度引擎 — 调度主循环、启停控制、任务分发
 */
@Service
@RequiredArgsConstructor
public class TaskSchedulerService {

    private static final Logger log = LoggerFactory.getLogger(TaskSchedulerService.class);

    private final DnScriptMapper scriptMapper;
    private final DnSyncTaskMapper syncTaskMapper;
    private final DnScriptFolderMapper folderMapper;
    private final DnSchedulerRunMapper runMapper;
    private final TaskDependencyService taskDependencyService;

    /** 使用 setter 注入打破与 TaskExecutionService 的循环依赖 */
    private TaskExecutionService taskExecutionService;

    @javax.annotation.Resource
    public void setTaskExecutionService(TaskExecutionService taskExecutionService) {
        this.taskExecutionService = taskExecutionService;
    }

    /** 调度开关 */
    private volatile boolean schedulerEnabled = false;

    /** 任务执行线程池 */
    private ExecutorService executor;

    /** 并发控制信号量：限制最大并行任务数 */
    private Semaphore concurrencySemaphore;
    private static final int MAX_CONCURRENT_TASKS = 4;

    /** 正在运行的任务 ID 集合，防止重复调度 */
    private final Set<String> runningTasks = ConcurrentHashMap.newKeySet();

    /** 用于任务超时控制的共享线程池（避免每次创建新线程池） */
    private ExecutorService timeoutExecutor;

    @PostConstruct
    public void init() {
        concurrencySemaphore = new Semaphore(MAX_CONCURRENT_TASKS);
        executor = new ThreadPoolExecutor(MAX_CONCURRENT_TASKS, MAX_CONCURRENT_TASKS * 2,
                60, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(200),
                r -> {
                    Thread t = new Thread(r, "scheduler-worker");
                    t.setDaemon(true);
                    return t;
                },
                new ThreadPoolExecutor.CallerRunsPolicy());
        timeoutExecutor = new ThreadPoolExecutor(MAX_CONCURRENT_TASKS, MAX_CONCURRENT_TASKS * 2,
                60, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(100),
                r -> {
                    Thread t = new Thread(r, "task-timeout");
                    t.setDaemon(true);
                    return t;
                },
                new ThreadPoolExecutor.CallerRunsPolicy());
        log.info("DataNote 调度引擎已初始化 (最大并发: {})", MAX_CONCURRENT_TASKS);

        // 启动时自动启用调度引擎，并恢复未完成任务
        schedulerEnabled = true;
        recoverOnStartup();
        catchUpMissedDays();
        log.info("调度引擎已自动启用");
    }

    /**
     * 服务启动时自动恢复：如果有 WAITING 或 RUNNING 状态的任务，自动启用调度
     */
    private void recoverOnStartup() {
        try {
            // 把上次中断的 RUNNING 任务重置为 WAITING
            QueryWrapper<DnSchedulerRun> runningQw = new QueryWrapper<>();
            runningQw.eq("status", DnSchedulerRun.STATUS_RUNNING);
            List<DnSchedulerRun> stuckRuns = runMapper.selectList(runningQw);
            for (DnSchedulerRun run : stuckRuns) {
                run.setStatus(DnSchedulerRun.STATUS_WAITING);
                run.setLog((run.getLog() != null ? run.getLog() : "") + "\n[RECOVER] 服务重启，任务重置为等待状态");
                runMapper.updateById(run);
            }

            // 检查是否有等待中的任务
            QueryWrapper<DnSchedulerRun> waitingQw = new QueryWrapper<>();
            waitingQw.eq("status", DnSchedulerRun.STATUS_WAITING);
            Long waitingCount = runMapper.selectCount(waitingQw);
            if (waitingCount > 0 || !stuckRuns.isEmpty()) {
                schedulerEnabled = true;
                log.info("调度引擎自动恢复：{} 个等待任务，{} 个中断任务已重置",
                        waitingCount, stuckRuns.size());
            }
        } catch (Exception e) {
            log.error("调度引擎恢复检查失败", e);
        }
    }

    // ======================== 暴露共享状态 ========================

    /**
     * 获取任务执行线程池（供 TaskExecutionService 使用）
     *
     * @return 任务执行线程池
     */
    public ExecutorService getExecutor() {
        return executor;
    }

    /**
     * 获取超时控制线程池（供 TaskExecutionService 使用）
     *
     * @return 超时控制线程池
     */
    public ExecutorService getTimeoutExecutor() {
        return timeoutExecutor;
    }

    /**
     * 判断任务是否正在运行
     *
     * @param taskKey 任务唯一标识
     * @return 是否运行中
     */
    public boolean isTaskRunning(String taskKey) {
        return runningTasks.contains(taskKey);
    }

    // ======================== 定时扫描 ========================

    /**
     * 启动时自动补漏：检查近3天是否有缺失的调度，有则自动创建
     */
    private void catchUpMissedDays() {
        try {
            for (int i = 1; i <= 3; i++) {
                LocalDate runDate = LocalDate.now().minusDays(i);
                QueryWrapper<DnSchedulerRun> qw = new QueryWrapper<>();
                qw.eq("run_date", runDate).eq("run_type", Constants.RUN_TYPE_DAILY);
                Long count = runMapper.selectCount(qw);
                if (count == 0) {
                    log.info("发现 {} 缺失调度记录，自动补创建", runDate);
                    startDailyRun(runDate);
                }
            }
        } catch (Exception e) {
            log.error("调度补漏失败", e);
        }
    }

    /**
     * 每天凌晨 00:10 自动创建当日调度（数据日期 = 昨天）
     */
    @Scheduled(cron = "0 10 0 * * ?")
    public void autoDailyRun() {
        try {
            LocalDate runDate = LocalDate.now().minusDays(1);
            log.info("自动创建每日调度 (runDate={})", runDate);
            startDailyRun(runDate);
        } catch (Exception e) {
            log.error("自动创建每日调度失败", e);
        }
    }

    /**
     * 定时扫描等待中的任务并触发执行（每 15 秒）
     */
    @Scheduled(fixedRate = 15000)
    public void tick() {
        if (!schedulerEnabled) return;
        try {
            // 扫描所有有 WAITING 记录的日期
            QueryWrapper<DnSchedulerRun> qw = new QueryWrapper<>();
            qw.eq("status", DnSchedulerRun.STATUS_WAITING)
              .select("DISTINCT run_date, run_type");
            List<DnSchedulerRun> waitingGroups = runMapper.selectList(qw);

            Set<String> processed = new HashSet<>();
            for (DnSchedulerRun g : waitingGroups) {
                String key = g.getRunDate() + ":" + g.getRunType();
                if (processed.add(key)) {
                    processWaitingTasks(g.getRunDate(), g.getRunType());
                }
            }
        } catch (Exception e) {
            log.error("调度扫描异常", e);
        }
    }

    // ======================== 调度控制 ========================

    /**
     * 获取调度引擎是否启用
     *
     * @return 是否启用
     */
    public boolean isEnabled() { return schedulerEnabled; }

    /**
     * 设置调度引擎启停状态
     *
     * @param enabled 是否启用
     */
    public void setEnabled(boolean enabled) {
        this.schedulerEnabled = enabled;
        log.info("调度引擎 {}", enabled ? "已启用" : "已停用");
    }

    /**
     * 启动每日调度
     */
    public Map<String, Object> startDailyRun(LocalDate runDate) {
        // 检查是否已有记录
        QueryWrapper<DnSchedulerRun> checkQw = new QueryWrapper<>();
        checkQw.eq("run_date", runDate).eq("run_type", Constants.RUN_TYPE_DAILY);
        Long existCount = runMapper.selectCount(checkQw);
        if (existCount > 0) {
            schedulerEnabled = true;
            processWaitingTasks(runDate, Constants.RUN_TYPE_DAILY);
            Map<String, Object> result = new HashMap<>();
            result.put("message", "今日调度已存在，已恢复执行");
            result.put("totalTasks", existCount);
            return result;
        }

        // 先刷新依赖关系
        taskDependencyService.refreshAllDependencies();

        List<DnScript> onlineScripts = getOnlineScripts();
        List<DnSyncTask> onlineSyncTasks = getOnlineSyncTasks();
        int created = 0;

        for (DnSyncTask task : onlineSyncTasks) {
            createRunRecord(task.getId(), Constants.TASK_TYPE_SYNC_TASK, runDate, Constants.RUN_TYPE_DAILY, null);
            created++;
        }
        for (DnScript script : onlineScripts) {
            createRunRecord(script.getId(), Constants.TASK_TYPE_SCRIPT, runDate, Constants.RUN_TYPE_DAILY, null);
            created++;
        }

        schedulerEnabled = true;
        log.info("今日调度已创建，共 {} 个任务 (runDate={})", created, runDate);

        processWaitingTasks(runDate, Constants.RUN_TYPE_DAILY);

        Map<String, Object> result = new HashMap<>();
        result.put("message", "今日调度已启动");
        result.put("totalTasks", created);
        result.put("syncTasks", onlineSyncTasks.size());
        result.put("scripts", onlineScripts.size());
        return result;
    }

    private void createRunRecord(Long taskId, String taskType, LocalDate runDate, String runType, String batchId) {
        DnSchedulerRun run = new DnSchedulerRun();
        run.setTaskId(taskId);
        run.setTaskType(taskType);
        run.setRunDate(runDate);
        run.setRunType(runType);
        run.setBatchId(batchId);
        run.setStatus(DnSchedulerRun.STATUS_WAITING);
        run.setRetryCount(0);
        run.setCreatedAt(LocalDateTime.now());
        runMapper.insert(run);
    }

    // ======================== 核心调度逻辑 ========================

    /**
     * 处理等待中的任务：检查依赖和 cron 时间，满足条件时提交执行
     *
     * @param runDate 运行日期
     * @param runType 运行类型（daily / backfill）
     */
    public void processWaitingTasks(LocalDate runDate, String runType) {
        QueryWrapper<DnSchedulerRun> qw = new QueryWrapper<>();
        qw.eq("run_date", runDate)
          .eq("run_type", runType)
          .eq("status", DnSchedulerRun.STATUS_WAITING);
        List<DnSchedulerRun> waitingRuns = runMapper.selectList(qw);

        for (DnSchedulerRun run : waitingRuns) {
            String taskKey = buildTaskKey(run);
            if (runningTasks.contains(taskKey)) continue;

            // 条件1：检查上游依赖是否全部完成
            boolean upstreamsReady;
            if (Constants.RUN_TYPE_BACKFILL.equals(runType) && run.getBatchId() != null) {
                upstreamsReady = taskDependencyService.allUpstreamsCompletedInBatch(run, run.getBatchId());
            } else {
                upstreamsReady = taskDependencyService.allUpstreamsCompleted(run.getTaskId(), run.getTaskType(), runDate, runType);
            }

            // 条件2：检查 cron 时间是否到达（补数据任务不受 cron 限制）
            boolean timeReady = Constants.RUN_TYPE_BACKFILL.equals(runType)
                    || isScheduleTimeReady(run.getTaskId(), run.getTaskType());

            if (upstreamsReady && timeReady) {
                runningTasks.add(taskKey);
                executor.submit(() -> {
                    try {
                        concurrencySemaphore.acquire();
                        try {
                            taskExecutionService.executeTaskWithTimeout(run);
                        } finally {
                            concurrencySemaphore.release();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        log.warn("任务被中断: {}", taskKey);
                    } finally {
                        runningTasks.remove(taskKey);
                    }
                });
            }
        }
    }

    /**
     * 检查任务的 cron 调度时间是否已到达。
     */
    private boolean isScheduleTimeReady(Long taskId, String taskType) {
        String cron = getTaskCron(taskId, taskType);
        if (cron == null || cron.trim().isEmpty()) {
            return true;
        }

        try {
            CronExpression cronExpr = CronExpression.parse(cron);
            LocalDateTime startOfToday = LocalDate.now().atStartOfDay();
            LocalDateTime nextTrigger = cronExpr.next(startOfToday.minusSeconds(1));

            if (nextTrigger == null) {
                return true;
            }

            LocalTime triggerTime = nextTrigger.toLocalTime();
            return !LocalTime.now().isBefore(triggerTime);
        } catch (IllegalArgumentException e) {
            log.warn("无法解析 cron 表达式 '{}' (taskId={}, taskType={})，默认放行", cron, taskId, taskType);
            return true;
        }
    }

    private String getTaskCron(Long taskId, String taskType) {
        if (Constants.TASK_TYPE_SYNC_TASK.equals(taskType)) {
            DnSyncTask task = syncTaskMapper.selectById(taskId);
            return task != null ? task.getScheduleCron() : null;
        } else {
            DnScript script = scriptMapper.selectById(taskId);
            return script != null ? script.getScheduleCron() : null;
        }
    }

    // ======================== 补数据 ========================

    /**
     * 启动补数据任务
     *
     * @param rootTaskId    根任务 ID
     * @param rootTaskType  根任务类型
     * @param runDate       补数据日期
     * @param selectedTasks 选中的下游任务列表
     * @return 补数据结果（含 batchId 和任务数量）
     */
    public Map<String, Object> startBackfill(Long rootTaskId, String rootTaskType,
                                              LocalDate runDate, List<Map<String, Object>> selectedTasks) {
        String batchId = "bf_" + System.currentTimeMillis();

        // 创建根任务记录
        createBackfillRun(rootTaskId, rootTaskType, runDate, batchId);

        if (selectedTasks != null) {
            for (Map<String, Object> st : selectedTasks) {
                Long taskId = Long.valueOf(st.get("taskId").toString());
                String taskType = (String) st.get("taskType");
                createBackfillRun(taskId, taskType, runDate, batchId);
            }
        }

        schedulerEnabled = true;
        processWaitingTasks(runDate, Constants.RUN_TYPE_BACKFILL);

        Map<String, Object> result = new HashMap<>();
        result.put("batchId", batchId);
        result.put("totalTasks", 1 + (selectedTasks != null ? selectedTasks.size() : 0));
        return result;
    }

    private void createBackfillRun(Long taskId, String taskType, LocalDate runDate, String batchId) {
        QueryWrapper<DnSchedulerRun> delQw = new QueryWrapper<>();
        delQw.eq("task_id", taskId).eq("task_type", taskType)
             .eq("run_date", runDate).eq("run_type", Constants.RUN_TYPE_BACKFILL).eq("batch_id", batchId);
        runMapper.delete(delQw);

        DnSchedulerRun run = new DnSchedulerRun();
        run.setTaskId(taskId);
        run.setTaskType(taskType);
        run.setRunDate(runDate);
        run.setRunType(Constants.RUN_TYPE_BACKFILL);
        run.setBatchId(batchId);
        run.setStatus(DnSchedulerRun.STATUS_WAITING);
        run.setRetryCount(0);
        run.setCreatedAt(LocalDateTime.now());
        runMapper.insert(run);
    }

    // ======================== 恢复暂停 ========================

    /**
     * 恢复指定日期所有暂停的任务为等待状态
     *
     * @param runDate 运行日期
     * @param runType 运行类型
     * @return 恢复的任务数量
     */
    public int resumePaused(LocalDate runDate, String runType) {
        QueryWrapper<DnSchedulerRun> qw = new QueryWrapper<>();
        qw.eq("run_date", runDate).eq("run_type", runType)
          .eq("status", DnSchedulerRun.STATUS_PAUSED);
        List<DnSchedulerRun> pausedRuns = runMapper.selectList(qw);

        for (DnSchedulerRun run : pausedRuns) {
            run.setStatus(DnSchedulerRun.STATUS_WAITING);
            runMapper.updateById(run);
        }

        if (!pausedRuns.isEmpty()) {
            processWaitingTasks(runDate, runType);
        }
        return pausedRuns.size();
    }

    // ======================== 查询 ========================

    /**
     * 获取指定日期的调度状态概览
     *
     * @param runDate 运行日期
     * @return 状态概览（含任务列表和统计数据）
     */
    public Map<String, Object> getTodayStatus(LocalDate runDate) {
        QueryWrapper<DnSchedulerRun> qw = new QueryWrapper<>();
        qw.eq("run_date", runDate).eq("run_type", Constants.RUN_TYPE_DAILY).orderByAsc("id");
        List<DnSchedulerRun> runs = runMapper.selectList(qw);

        // 批量查询所有涉及的 script 和 syncTask，避免 N+1 查询
        Set<Long> scriptIds = new HashSet<>();
        Set<Long> syncTaskIds = new HashSet<>();
        for (DnSchedulerRun run : runs) {
            if (Constants.TASK_TYPE_SCRIPT.equals(run.getTaskType())) {
                scriptIds.add(run.getTaskId());
            } else {
                syncTaskIds.add(run.getTaskId());
            }
        }
        Map<Long, DnScript> scriptMap = scriptIds.isEmpty() ? Collections.emptyMap()
                : scriptMapper.selectBatchIds(scriptIds).stream()
                    .collect(Collectors.toMap(DnScript::getId, s -> s, (a, b) -> a));
        Map<Long, DnSyncTask> syncTaskMap = syncTaskIds.isEmpty() ? Collections.emptyMap()
                : syncTaskMapper.selectBatchIds(syncTaskIds).stream()
                    .collect(Collectors.toMap(DnSyncTask::getId, t -> t, (a, b) -> a));

        List<Map<String, Object>> runList = new ArrayList<>();
        for (DnSchedulerRun run : runs) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", run.getId());
            item.put("taskId", run.getTaskId());
            item.put("taskType", run.getTaskType());
            item.put("status", run.getStatus());
            item.put("startTime", run.getStartTime());
            item.put("endTime", run.getEndTime());
            item.put("retryCount", run.getRetryCount());

            if (Constants.TASK_TYPE_SCRIPT.equals(run.getTaskType())) {
                DnScript s = scriptMap.get(run.getTaskId());
                item.put("taskName", s != null ? s.getScriptName() : "未知脚本");
                item.put("layer", s != null ? taskDependencyService.getScriptLayer(s.getFolderId()) : "");
            } else {
                DnSyncTask t = syncTaskMap.get(run.getTaskId());
                item.put("taskName", t != null ? t.getTaskName() : "未知任务");
                item.put("layer", "ODS");
            }
            runList.add(item);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("runs", runList);
        result.put("total", runs.size());
        result.put("success", runs.stream().filter(r -> r.getStatus() == DnSchedulerRun.STATUS_SUCCESS).count());
        result.put("running", runs.stream().filter(r -> r.getStatus() == DnSchedulerRun.STATUS_RUNNING).count());
        result.put("waiting", runs.stream().filter(r -> r.getStatus() == DnSchedulerRun.STATUS_WAITING).count());
        result.put("failed", runs.stream().filter(r -> r.getStatus() == DnSchedulerRun.STATUS_FAILED).count());
        result.put("paused", runs.stream().filter(r -> r.getStatus() == DnSchedulerRun.STATUS_PAUSED).count());
        result.put("schedulerEnabled", schedulerEnabled);
        return result;
    }

    /**
     * 获取指定运行记录的执行日志
     *
     * @param runId 运行记录 ID
     * @return 日志内容
     */
    public String getRunLog(Long runId) {
        DnSchedulerRun run = runMapper.selectById(runId);
        return run != null ? run.getLog() : null;
    }

    /**
     * 停止运行中的任务
     */
    public void stopTask(Long runId) {
        DnSchedulerRun run = runMapper.selectById(runId);
        if (run == null) {
            throw new com.datanote.exception.ResourceNotFoundException("调度记录");
        }
        if (run.getStatus() != DnSchedulerRun.STATUS_RUNNING) {
            throw new com.datanote.exception.BusinessException("任务不在运行中，无法停止");
        }
        // Mark as failed
        DnSchedulerRun update = new DnSchedulerRun();
        update.setId(runId);
        update.setStatus(DnSchedulerRun.STATUS_FAILED);
        update.setEndTime(LocalDateTime.now());
        update.setLog(run.getLog() != null ? run.getLog() + "\n[SYSTEM] 任务被手动停止" : "[SYSTEM] 任务被手动停止");
        runMapper.updateById(update);
        // Remove from running set
        String taskKey = run.getTaskType() + "_" + run.getTaskId();
        runningTasks.remove(taskKey);
    }

    // ======================== 任务运行记录查询 ========================

    /**
     * 批量重跑：将指定任务（或当天全部失败/暂停任务）重置为 WAITING，调度引擎按依赖自动执行
     */
    public int batchRetry(List<Integer> runIds, String date) {
        List<DnSchedulerRun> toRetry;

        if (runIds != null && !runIds.isEmpty()) {
            // 按选中的 ID 重跑
            List<Long> ids = new ArrayList<>();
            for (Integer id : runIds) ids.add(id.longValue());
            toRetry = runMapper.selectBatchIds(ids);
        } else if (date != null && !date.isEmpty()) {
            // 未选中则重跑当天全部失败+暂停
            QueryWrapper<DnSchedulerRun> qw = new QueryWrapper<>();
            qw.eq("run_date", LocalDate.parse(date))
              .eq("run_type", Constants.RUN_TYPE_DAILY)
              .in("status", DnSchedulerRun.STATUS_FAILED, DnSchedulerRun.STATUS_PAUSED);
            toRetry = runMapper.selectList(qw);
        } else {
            return 0;
        }

        int count = 0;
        for (DnSchedulerRun run : toRetry) {
            if (run.getStatus() == DnSchedulerRun.STATUS_FAILED || run.getStatus() == DnSchedulerRun.STATUS_PAUSED) {
                run.setStatus(DnSchedulerRun.STATUS_WAITING);
                run.setRetryCount(0);
                run.setLog(null);
                run.setStartTime(null);
                run.setEndTime(null);
                runMapper.updateById(run);
                count++;
            }
        }

        if (count > 0) {
            schedulerEnabled = true;
            // 触发调度引擎立即检查
            LocalDate runDate = toRetry.get(0).getRunDate();
            processWaitingTasks(runDate, Constants.RUN_TYPE_DAILY);
            log.info("批量重跑 {} 个任务，调度引擎已触发", count);
        }
        return count;
    }

    /**
     * 查询指定任务的本地运行记录（按时间倒序）
     */
    public List<Map<String, Object>> getTaskRuns(Long taskId, String taskType, int limit) {
        QueryWrapper<DnSchedulerRun> qw = new QueryWrapper<>();
        qw.eq("task_id", taskId).eq("task_type", taskType)
          .orderByDesc("id").last("LIMIT " + limit);
        List<DnSchedulerRun> runs = runMapper.selectList(qw);

        List<Map<String, Object>> result = new ArrayList<>();
        for (DnSchedulerRun run : runs) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", run.getId());
            item.put("runDate", run.getRunDate());
            item.put("runType", run.getRunType());
            item.put("status", run.getStatus());
            item.put("startTime", run.getStartTime());
            item.put("endTime", run.getEndTime());
            item.put("retryCount", run.getRetryCount());
            // 计算耗时（秒）
            long duration = 0;
            if (run.getStartTime() != null && run.getEndTime() != null) {
                duration = java.time.Duration.between(run.getStartTime(), run.getEndTime()).getSeconds();
            }
            item.put("duration", duration);
            result.add(item);
        }
        return result;
    }

    // ======================== 工具方法 ========================

    private String buildTaskKey(DnSchedulerRun run) {
        String base = run.getTaskType() + ":" + run.getTaskId() + ":" + run.getRunDate() + ":" + run.getRunType();
        return run.getBatchId() != null ? base + ":" + run.getBatchId() : base;
    }

    private List<DnScript> getOnlineScripts() {
        QueryWrapper<DnScript> qw = new QueryWrapper<>();
        qw.eq("schedule_status", Constants.SCHEDULE_ONLINE);
        return scriptMapper.selectList(qw);
    }

    private List<DnSyncTask> getOnlineSyncTasks() {
        QueryWrapper<DnSyncTask> qw = new QueryWrapper<>();
        qw.eq("schedule_status", Constants.SCHEDULE_ONLINE);
        return syncTaskMapper.selectList(qw);
    }
}
