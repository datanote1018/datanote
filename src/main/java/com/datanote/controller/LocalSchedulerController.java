package com.datanote.controller;

import com.alibaba.fastjson.JSONArray;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.datanote.common.Constants;
import com.datanote.dto.BackfillRequest;
import com.datanote.dto.PauseDownstreamRequest;
import com.datanote.exception.BusinessException;
import com.datanote.exception.ResourceNotFoundException;
import com.datanote.mapper.DnScriptMapper;
import com.datanote.mapper.DnScriptVersionMapper;
import com.datanote.mapper.DnSyncTaskMapper;
import com.datanote.model.DnScript;
import com.datanote.model.DnScriptVersion;
import com.datanote.model.DnSyncTask;
import com.datanote.model.R;
import com.datanote.service.DolphinService;
import com.datanote.service.TaskDependencyService;
import com.datanote.service.TaskExecutionService;
import com.datanote.service.TaskSchedulerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import com.datanote.model.dto.CronPreviewRequest;
import org.springframework.scheduling.support.CronExpression;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 本地调度引擎 Controller — 上下线、执行控制、日志查询
 */
@Slf4j
@RestController
@RequestMapping("/api/scheduler")
@RequiredArgsConstructor
@Tag(name = "本地调度引擎", description = "任务上下线、执行控制、重试、补数据、日志查询")
public class LocalSchedulerController {

    private final DolphinService dolphinService;
    private final DnScriptMapper scriptMapper;
    private final DnSyncTaskMapper syncTaskMapper;
    private final DnScriptVersionMapper scriptVersionMapper;
    private final TaskSchedulerService taskSchedulerService;
    private final TaskExecutionService taskExecutionService;
    private final TaskDependencyService taskDependencyService;

    @Value("${datax.home}")
    private String dataxHome;

    @Value("${datax.job-dir}")
    private String jobDir;

    // ========== 脚本上下线 ==========

    /**
     * 上线：将脚本推送到 DolphinScheduler 并启动调度
     */
    @PostMapping("/online/{scriptId}")
    @Operation(summary = "脚本上线（DS远程调度）")
    public R<Map<String, Object>> online(@PathVariable Long scriptId) {
        try {
            DnScript script = requireScript(scriptId);
            requireNotEmpty(script.getContent(), "脚本内容为空，无法上线");
            requireNotEmpty(script.getScheduleCron(), "请先配置调度 cron 表达式");

            Map<String, Object> dsResult = dolphinService.onlineScript(
                    script.getScriptName(),
                    script.getScriptType(),
                    script.getContent(),
                    script.getDsWorkflowCode(),
                    script.getDsTaskCode(),
                    script.getDsScheduleId(),
                    script.getScheduleCron(),
                    defaultInt(script.getTimeoutSeconds(), Constants.DEFAULT_TIMEOUT_SECONDS),
                    defaultInt(script.getRetryTimes(), Constants.DEFAULT_RETRY_TIMES),
                    defaultInt(script.getRetryInterval(), Constants.DEFAULT_RETRY_INTERVAL),
                    script.getWarningType()
            );

            DnScript update = new DnScript();
            update.setId(scriptId);
            update.setDsProjectCode((Long) dsResult.get("dsProjectCode"));
            update.setDsWorkflowCode((Long) dsResult.get("dsWorkflowCode"));
            update.setDsTaskCode((Long) dsResult.get("dsTaskCode"));
            update.setDsScheduleId((Integer) dsResult.get("dsScheduleId"));
            update.setScheduleStatus(Constants.SCHEDULE_ONLINE);
            scriptMapper.updateById(update);

            return R.ok(dsResult);
        } catch (Exception e) {
            log.error("脚本上线失败, scriptId={}", scriptId, e);
            return R.fail("脚本上线失败");
        }
    }

    /**
     * 下线：停止 DolphinScheduler 中的调度
     */
    @PostMapping("/offline/{scriptId}")
    @Operation(summary = "脚本下线（DS远程调度）")
    public R<Void> offline(@PathVariable Long scriptId) {
        try {
            DnScript script = requireScript(scriptId);
            if (script.getDsWorkflowCode() == null || script.getDsWorkflowCode() == 0) {
                throw new BusinessException("该脚本尚未上线过");
            }
            dolphinService.offlineScript(script.getDsWorkflowCode(), script.getDsScheduleId());

            DnScript update = new DnScript();
            update.setId(scriptId);
            update.setScheduleStatus(Constants.SCHEDULE_OFFLINE);
            scriptMapper.updateById(update);
            return R.ok();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("脚本下线失败, scriptId={}", scriptId, e);
            return R.fail("脚本下线失败");
        }
    }

    /**
     * 脚本上线（本地调度）
     */
    @PostMapping("/local-online/{scriptId}")
    @Operation(summary = "脚本上线（本地调度）")
    public R<Void> localOnline(@PathVariable Long scriptId) {
        DnScript script = requireScript(scriptId);
        requireNotEmpty(script.getContent(), "脚本内容为空，无法上线");

        // 上线时创建版本快照
        createOnlineVersion(script);

        DnScript update = new DnScript();
        update.setId(scriptId);
        update.setScheduleStatus(Constants.SCHEDULE_ONLINE);
        scriptMapper.updateById(update);
        taskDependencyService.refreshAllDependencies();
        return R.ok();
    }

    /**
     * 脚本下线（本地调度）
     */
    @PostMapping("/local-offline/{scriptId}")
    @Operation(summary = "脚本下线（本地调度）")
    public R<Void> localOffline(@PathVariable Long scriptId) {
        DnScript update = new DnScript();
        update.setId(scriptId);
        update.setScheduleStatus(Constants.SCHEDULE_OFFLINE);
        scriptMapper.updateById(update);
        return R.ok();
    }

    // ========== 同步任务上下线 ==========

    /**
     * 同步任务上线：生成 DataX shell 脚本推送到 DS
     */
    @PostMapping("/sync-online/{taskId}")
    @Operation(summary = "同步任务上线（DS远程调度）")
    public R<Map<String, Object>> syncOnline(@PathVariable Long taskId) {
        try {
            DnSyncTask task = requireSyncTask(taskId);
            requireNotEmpty(task.getScheduleCron(), "请先配置调度 cron 表达式");

            String jobFile = jobDir + "/" + task.getTargetTable() + ".json";
            String shellScript = "#!/bin/bash\n"
                    + "# DataNote 同步任务: " + task.getTaskName() + "\n"
                    + "DT=${1:-$(date -d '-1 day' +%Y-%m-%d)}\n"
                    + "echo \"同步任务: " + task.getSourceDb() + "." + task.getSourceTable()
                    + " -> ods." + task.getTargetTable() + ", dt=$DT\"\n"
                    + "java -server -Xms1g -Xmx1g"
                    + " -Ddatax.home=" + dataxHome
                    + " -classpath " + dataxHome + "/lib/*"
                    + " com.alibaba.datax.core.Engine"
                    + " -mode standalone -jobid -1"
                    + " -job " + jobFile + "\n";

            Map<String, Object> dsResult = dolphinService.onlineScript(
                    task.getTaskName(),
                    Constants.SCRIPT_TYPE_SHELL,
                    shellScript,
                    task.getDsWorkflowCode(),
                    task.getDsTaskCode(),
                    task.getDsScheduleId(),
                    task.getScheduleCron(),
                    defaultInt(task.getTimeoutSeconds(), Constants.DEFAULT_TIMEOUT_SECONDS),
                    defaultInt(task.getRetryTimes(), Constants.DEFAULT_RETRY_TIMES),
                    defaultInt(task.getRetryInterval(), Constants.DEFAULT_RETRY_INTERVAL),
                    task.getWarningType()
            );

            DnSyncTask update = new DnSyncTask();
            update.setId(taskId);
            update.setDsProjectCode((Long) dsResult.get("dsProjectCode"));
            update.setDsWorkflowCode((Long) dsResult.get("dsWorkflowCode"));
            update.setDsTaskCode((Long) dsResult.get("dsTaskCode"));
            update.setDsScheduleId((Integer) dsResult.get("dsScheduleId"));
            update.setScheduleStatus(Constants.SCHEDULE_ONLINE);
            syncTaskMapper.updateById(update);
            return R.ok(dsResult);
        } catch (Exception e) {
            log.error("同步任务上线失败, taskId={}", taskId, e);
            return R.fail("同步任务上线失败");
        }
    }

    /**
     * 同步任务下线
     */
    @PostMapping("/sync-offline/{taskId}")
    @Operation(summary = "同步任务下线（DS远程调度）")
    public R<Void> syncOffline(@PathVariable Long taskId) {
        try {
            DnSyncTask task = requireSyncTask(taskId);
            if (task.getDsWorkflowCode() == null || task.getDsWorkflowCode() == 0) {
                throw new BusinessException("该任务尚未上线过");
            }
            dolphinService.offlineScript(task.getDsWorkflowCode(), task.getDsScheduleId());

            DnSyncTask update = new DnSyncTask();
            update.setId(taskId);
            update.setScheduleStatus(Constants.SCHEDULE_OFFLINE);
            syncTaskMapper.updateById(update);
            return R.ok();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("同步任务下线失败, taskId={}", taskId, e);
            return R.fail("同步任务下线失败");
        }
    }

    /**
     * 同步任务上线（本地调度）
     */
    @PostMapping("/sync-local-online/{taskId}")
    @Operation(summary = "同步任务上线（本地调度）")
    public R<Void> syncLocalOnline(@PathVariable Long taskId) {
        requireSyncTask(taskId);
        DnSyncTask update = new DnSyncTask();
        update.setId(taskId);
        update.setScheduleStatus(Constants.SCHEDULE_ONLINE);
        syncTaskMapper.updateById(update);
        taskDependencyService.refreshAllDependencies();
        return R.ok();
    }

    /**
     * 同步任务下线（本地调度）
     */
    @PostMapping("/sync-local-offline/{taskId}")
    @Operation(summary = "同步任务下线（本地调度）")
    public R<Void> syncLocalOffline(@PathVariable Long taskId) {
        DnSyncTask update = new DnSyncTask();
        update.setId(taskId);
        update.setScheduleStatus(Constants.SCHEDULE_OFFLINE);
        syncTaskMapper.updateById(update);
        return R.ok();
    }

    // ========== 调度引擎控制 ==========

    /**
     * 获取今日调度状态
     */
    @GetMapping("/today")
    @Operation(summary = "获取今日调度状态")
    public R<Map<String, Object>> getTodayStatus(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        LocalDate runDate = date != null ? date : LocalDate.now().minusDays(1);
        return R.ok(taskSchedulerService.getTodayStatus(runDate));
    }

    /**
     * 启动今日每日调度
     */
    @PostMapping("/start-daily")
    @Operation(summary = "启动每日调度")
    public R<Map<String, Object>> startDailyRun(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        LocalDate runDate = date != null ? date : LocalDate.now().minusDays(1);
        return R.ok(taskSchedulerService.startDailyRun(runDate));
    }

    /**
     * 停止调度引擎
     */
    @PostMapping("/stop")
    @Operation(summary = "停止调度引擎")
    public R<Void> stopScheduler() {
        taskSchedulerService.setEnabled(false);
        return R.ok();
    }

    /**
     * 重试失败的任务
     */
    @PostMapping("/retry/{runId}")
    @Operation(summary = "重试失败的任务")
    public R<Void> retryTask(@PathVariable Long runId) {
        taskExecutionService.retryTask(runId);
        return R.ok();
    }

    /**
     * 暂停指定任务的所有下游
     */
    @PostMapping("/pause-downstream")
    @Operation(summary = "暂停指定任务的所有下游")
    public R<Map<String, Object>> pauseDownstreamByTask(@RequestBody PauseDownstreamRequest req) {
        LocalDate runDate = req.getRunDate() != null ? req.getRunDate() : LocalDate.now().minusDays(1);
        String runType = req.getRunType() != null ? req.getRunType() : Constants.RUN_TYPE_DAILY;

        int paused = taskDependencyService.pauseDownstream(req.getTaskId(), req.getTaskType(), runDate, runType);
        Map<String, Object> result = new HashMap<>();
        result.put("pausedCount", paused);
        return R.ok(result);
    }

    /**
     * 恢复所有暂停的任务
     */
    @PostMapping("/resume")
    @Operation(summary = "恢复所有暂停的任务")
    public R<Map<String, Object>> resumePaused(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        LocalDate runDate = date != null ? date : LocalDate.now().minusDays(1);
        int resumed = taskSchedulerService.resumePaused(runDate, Constants.RUN_TYPE_DAILY);
        Map<String, Object> result = new HashMap<>();
        result.put("resumedCount", resumed);
        return R.ok(result);
    }

    /**
     * 补数据
     */
    @PostMapping("/backfill")
    @Operation(summary = "补数据")
    public R<Map<String, Object>> startBackfill(@RequestBody BackfillRequest req) {
        return R.ok(taskSchedulerService.startBackfill(
                req.getTaskId(), req.getTaskType(), req.getRunDate(), req.getSelectedTasks()));
    }

    /**
     * Cron 表达式预览：返回未来 N 次执行时间
     */
    @PostMapping("/cron-preview")
    @Operation(summary = "Cron表达式预览")
    public R<List<String>> cronPreview(@RequestBody CronPreviewRequest req) {
        String cronExpr = req.getCron();
        int count = req.getCount() != null ? req.getCount() : 5;
        if (cronExpr == null || cronExpr.trim().isEmpty()) {
            return R.fail("Cron 表达式不能为空");
        }
        try {
            CronExpression expr = CronExpression.parse(cronExpr);
            List<String> times = new ArrayList<>();
            LocalDateTime next = LocalDateTime.now();
            java.time.format.DateTimeFormatter fmt = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            for (int i = 0; i < count; i++) {
                next = expr.next(next);
                if (next == null) break;
                times.add(next.format(fmt));
            }
            return R.ok(times);
        } catch (Exception e) {
            return R.fail("无效的 Cron 表达式: " + e.getMessage());
        }
    }

    /**
     * 重新调度失败任务
     */
    @PostMapping("/rerun/{runId}")
    @Operation(summary = "重新调度")
    public R<Void> rerunTask(@PathVariable Long runId) {
        taskExecutionService.retryTask(runId);
        return R.ok();
    }

    /**
     * 停止运行中的任务
     */
    @PostMapping("/stop-task/{runId}")
    @Operation(summary = "停止运行中的任务")
    public R<Void> stopTask(@PathVariable Long runId) {
        taskSchedulerService.stopTask(runId);
        return R.ok();
    }

    // ========== 日志查询 ==========

    /**
     * 查询 DS 调度执行日志
     */
    @GetMapping("/logs/{scriptId}")
    @Operation(summary = "查询DS调度执行日志")
    public R<JSONArray> getLogs(@PathVariable Long scriptId,
                               @RequestParam(defaultValue = "1") int pageNo,
                               @RequestParam(defaultValue = "20") int pageSize) {
        try {
            DnScript script = requireScript(scriptId);
            if (script.getDsWorkflowCode() == null || script.getDsWorkflowCode() == 0) {
                return R.ok(new JSONArray());
            }
            JSONArray instances = dolphinService.getTaskInstances(script.getScriptName(), pageNo, pageSize);
            return R.ok(instances != null ? instances : new JSONArray());
        } catch (Exception e) {
            log.error("查询调度执行日志失败, scriptId={}", scriptId, e);
            return R.fail("查询日志失败");
        }
    }

    /**
     * 读取某次调度执行的详细日志
     */
    @GetMapping("/log-detail/{taskInstanceId}")
    @Operation(summary = "读取DS调度执行详细日志")
    public R<String> getLogDetail(@PathVariable int taskInstanceId,
                                  @RequestParam(defaultValue = "0") int skipLineNum,
                                  @RequestParam(defaultValue = "1000") int limit) {
        try {
            return R.ok(dolphinService.getTaskLog(taskInstanceId, skipLineNum, limit));
        } catch (Exception e) {
            log.error("读取调度执行日志详情失败, taskInstanceId={}", taskInstanceId, e);
            return R.fail("读取日志详情失败");
        }
    }

    /**
     * 获取任务执行日志
     */
    @GetMapping("/run-log/{runId}")
    @Operation(summary = "获取本地任务执行日志")
    public R<String> getRunLog(@PathVariable Long runId) {
        String logContent = taskSchedulerService.getRunLog(runId);
        return R.ok(logContent != null ? logContent : "");
    }

    /**
     * 批量重跑：将选中的失败/暂停任务重置为等待，调度引擎按依赖顺序自动执行
     */
    @PostMapping("/batch-retry")
    @Operation(summary = "批量重跑任务")
    public R<Map<String, Object>> batchRetry(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Integer> runIds = (List<Integer>) body.get("runIds");
        String date = (String) body.get("date");
        int count = taskSchedulerService.batchRetry(runIds, date);
        Map<String, Object> result = new HashMap<>();
        result.put("retried", count);
        return R.ok(result);
    }

    /**
     * 查询指定任务的本地调度运行记录（近 30 天）
     */
    @GetMapping("/task-runs")
    @Operation(summary = "查询任务本地运行记录")
    public R<List<Map<String, Object>>> getTaskRuns(
            @RequestParam Long taskId, @RequestParam String taskType,
            @RequestParam(defaultValue = "20") int limit) {
        return R.ok(taskSchedulerService.getTaskRuns(taskId, taskType, limit));
    }

    // ========== 内部辅助方法 ==========

    private DnScript requireScript(Long scriptId) {
        DnScript script = scriptMapper.selectById(scriptId);
        if (script == null) {
            throw new ResourceNotFoundException("脚本");
        }
        return script;
    }

    private DnSyncTask requireSyncTask(Long taskId) {
        DnSyncTask task = syncTaskMapper.selectById(taskId);
        if (task == null) {
            throw new ResourceNotFoundException("同步任务");
        }
        return task;
    }

    private void requireNotEmpty(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new BusinessException(message);
        }
    }

    private int defaultInt(Integer value, int defaultVal) {
        return value != null ? value : defaultVal;
    }

    /**
     * 上线时创建版本快照，commitMsg 包含"上线"以区分
     */
    private void createOnlineVersion(DnScript script) {
        String content = script.getContent();
        if (content == null || content.trim().isEmpty()) return;

        QueryWrapper<DnScriptVersion> qw = new QueryWrapper<>();
        qw.eq("script_id", script.getId()).orderByDesc("version").last("LIMIT 1");
        DnScriptVersion latest = scriptVersionMapper.selectOne(qw);

        DnScriptVersion ver = new DnScriptVersion();
        ver.setScriptId(script.getId());
        ver.setVersion(latest == null ? 1 : latest.getVersion() + 1);
        ver.setContent(content);
        ver.setCommitMsg("上线版本快照");
        ver.setCommittedBy("system");
        ver.setCommittedAt(LocalDateTime.now());
        ver.setVersionType("online");
        scriptVersionMapper.insert(ver);
    }
}
