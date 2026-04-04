package com.datanote.controller;

import com.datanote.mapper.DnDatasourceMapper;
import com.datanote.mapper.DnTaskExecutionMapper;
import com.datanote.model.ColumnInfo;
import com.datanote.model.DnDatasource;
import com.datanote.model.DnTaskExecution;
import com.datanote.model.R;
import com.datanote.model.dto.DataxCreateAndSyncRequest;
import com.datanote.model.dto.DataxGenerateJobRequest;
import com.datanote.model.dto.DataxRunRequest;
import com.datanote.service.DataxService;
import com.datanote.service.HiveService;
import com.datanote.service.MetadataService;
import com.datanote.util.ProcessUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * DataX 同步管理 Controller
 */
@Slf4j
@RestController
@Tag(name = "DataX 数据同步", description = "MySQL 到 Hive 的数据同步管理")
@RequestMapping("/api/datax")
@RequiredArgsConstructor
public class DataxController {

    private final DataxService dataxService;
    private final MetadataService metadataService;
    private final HiveService hiveService;
    private final DnDatasourceMapper datasourceMapper;
    private final DnTaskExecutionMapper taskExecutionMapper;

    @Value("${spring.datasource.url:}")
    private String defaultDbUrl;

    @Value("${spring.datasource.username:root}")
    private String defaultDbUser;

    @Value("${spring.datasource.password:}")
    private String defaultDbPass;

    /**
     * 生成 DataX JSON 配置
     */
    @Operation(summary = "生成 DataX 任务配置")
    @PostMapping("/generate-job")
    public R<Map<String, String>> generateJob(@RequestBody DataxGenerateJobRequest body) {
        try {
            String db = body.getDb();
            String table = body.getTable();
            String syncMode = body.getSyncMode() != null ? body.getSyncMode() : "df";
            DnDatasource ds = resolveDatasource(body.getDatasourceId());

            List<ColumnInfo> columns = metadataService.getColumns(db, table);
            String odsTable = hiveService.getOdsTableName(db, table, syncMode);
            String jobPath = dataxService.generateJobJson(
                    ds.getHost(), ds.getPort(), ds.getUsername(), ds.getPassword(),
                    db, table, odsTable, columns);

            Map<String, String> result = new HashMap<>();
            result.put("jobPath", jobPath);
            result.put("odsTable", odsTable);
            return R.ok(result);
        } catch (Exception e) {
            log.error("生成 DataX 任务配置失败", e);
            return R.fail("生成任务配置失败");
        }
    }

    /**
     * 执行 DataX 同步任务
     */
    @Operation(summary = "执行 DataX 同步任务")
    @PostMapping("/run")
    public R<Map<String, Object>> run(@RequestBody DataxRunRequest body) {
        try {
            String jobPath = body.getJobPath();
            ProcessUtil.ExecResult execResult = dataxService.runJob(jobPath);

            Map<String, Object> data = new HashMap<>();
            data.put("exitCode", execResult.getExitCode());
            data.put("durationMs", execResult.getDurationMs());
            data.put("output", execResult.getOutput());
            data.put("success", execResult.getExitCode() == 0);
            return R.ok(data);
        } catch (Exception e) {
            log.error("执行 DataX 同步任务失败", e);
            return R.fail("执行同步任务失败");
        }
    }

    /**
     * 一键建表并同步
     */
    @Operation(summary = "一键建表并同步数据")
    @PostMapping("/create-and-sync")
    public R<Map<String, Object>> createAndSync(@RequestBody DataxCreateAndSyncRequest body) {
        long startMs = System.currentTimeMillis();
        // 创建执行记录
        DnTaskExecution exec = new DnTaskExecution();
        exec.setSyncTaskId(body.getSyncTaskId());
        exec.setTaskType("syncTask");
        exec.setTriggerType("manual");
        exec.setStatus("RUNNING");
        exec.setStartTime(java.time.LocalDateTime.now());
        if (body.getSyncTaskId() != null) {
            taskExecutionMapper.insert(exec);
        }

        try {
            String db = body.getDb();
            String table = body.getTable();
            String syncMode = body.getSyncMode() != null ? body.getSyncMode() : "df";
            DnDatasource ds = resolveDatasource(body.getDatasourceId());

            List<ColumnInfo> columns = metadataService.getColumns(db, table);
            String odsTable = hiveService.getOdsTableName(db, table, syncMode);

            String ddl = hiveService.generateDDL(db, table, columns, syncMode);
            hiveService.executeDDL(ddl);

            String today = java.time.LocalDate.now().minusDays(1).toString();
            String addPartitionSql = "ALTER TABLE ods." + odsTable
                    + " ADD IF NOT EXISTS PARTITION (dt='" + today + "')";
            hiveService.executeDDL(addPartitionSql);

            String jobPath = dataxService.generateJobJson(
                    ds.getHost(), ds.getPort(), ds.getUsername(), ds.getPassword(),
                    db, table, odsTable, columns);

            ProcessUtil.ExecResult execResult = dataxService.runJob(jobPath);

            // 执行完成后清理含密码的 JSON 配置文件
            try { new java.io.File(jobPath).delete(); } catch (Exception ignored) {}

            // 更新执行记录
            if (exec.getId() != null) {
                exec.setStatus(execResult.getExitCode() == 0 ? "SUCCESS" : "FAILED");
                exec.setEndTime(java.time.LocalDateTime.now());
                exec.setDuration((int)((System.currentTimeMillis() - startMs) / 1000));
                exec.setLog(execResult.getOutput() != null && execResult.getOutput().length() > 50000
                        ? execResult.getOutput().substring(execResult.getOutput().length() - 50000)
                        : execResult.getOutput());
                taskExecutionMapper.updateById(exec);
            }

            Map<String, Object> data = new HashMap<>();
            data.put("odsTable", odsTable);
            data.put("ddl", ddl);
            data.put("exitCode", execResult.getExitCode());
            data.put("durationMs", execResult.getDurationMs());
            data.put("success", execResult.getExitCode() == 0);
            data.put("output", execResult.getOutput());
            return R.ok(data);
        } catch (Exception e) {
            log.error("一键建表并同步失败", e);
            // 更新执行记录为失败
            if (exec.getId() != null) {
                exec.setStatus("FAILED");
                exec.setEndTime(java.time.LocalDateTime.now());
                exec.setDuration((int)((System.currentTimeMillis() - startMs) / 1000));
                exec.setLog(e.getMessage());
                taskExecutionMapper.updateById(exec);
            }
            return R.fail("建表同步操作失败");
        }
    }

    private DnDatasource resolveDatasource(String dsIdStr) {
        if (dsIdStr != null && !dsIdStr.isEmpty()) {
            try {
                DnDatasource ds = datasourceMapper.selectById(Long.valueOf(dsIdStr));
                if (ds != null) return ds;
            } catch (NumberFormatException ignored) {}
        }
        return getDefaultDatasource();
    }

    private DnDatasource getDefaultDatasource() {
        DnDatasource ds = new DnDatasource();
        try {
            String hostPort = defaultDbUrl.split("//")[1].split("/")[0];
            ds.setHost(hostPort.split(":")[0]);
            ds.setPort(Integer.parseInt(hostPort.split(":")[1]));
        } catch (Exception e) {
            ds.setHost("127.0.0.1");
            ds.setPort(3306);
        }
        ds.setUsername(defaultDbUser);
        ds.setPassword(defaultDbPass);
        return ds;
    }
}
