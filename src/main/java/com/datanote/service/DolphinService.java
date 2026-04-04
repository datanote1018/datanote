package com.datanote.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * DolphinScheduler REST API 封装
 */
@Service
public class DolphinService {

    private static final Logger log = LoggerFactory.getLogger(DolphinService.class);

    @Value("${dolphin.api-url}")
    private String apiUrl;

    @Value("${dolphin.token}")
    private String token;

    @Value("${dolphin.project-code:0}")
    private long projectCode;

    @Value("${dolphin.tenant-code:default}")
    private String tenantCode;

    // ======================== 通用 HTTP 方法 ========================

    private JSONObject doGet(String path) throws Exception {
        URL url = new URL(apiUrl + path);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("token", token);
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(30000);
        return readResponse(conn);
    }

    private JSONObject doPost(String path, Map<String, String> params) throws Exception {
        URL url = new URL(apiUrl + path);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("token", token);
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        conn.setDoOutput(true);
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(30000);

        if (params != null && !params.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, String> e : params.entrySet()) {
                if (sb.length() > 0) sb.append("&");
                sb.append(URLEncoder.encode(e.getKey(), "UTF-8"))
                  .append("=")
                  .append(URLEncoder.encode(e.getValue(), "UTF-8"));
            }
            try (OutputStream os = conn.getOutputStream()) {
                os.write(sb.toString().getBytes(StandardCharsets.UTF_8));
            }
        }
        return readResponse(conn);
    }

    private JSONObject doPut(String path, Map<String, String> params) throws Exception {
        URL url = new URL(apiUrl + path);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("PUT");
        conn.setRequestProperty("token", token);
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        conn.setDoOutput(true);
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(30000);

        if (params != null && !params.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, String> e : params.entrySet()) {
                if (sb.length() > 0) sb.append("&");
                sb.append(URLEncoder.encode(e.getKey(), "UTF-8"))
                  .append("=")
                  .append(URLEncoder.encode(e.getValue(), "UTF-8"));
            }
            try (OutputStream os = conn.getOutputStream()) {
                os.write(sb.toString().getBytes(StandardCharsets.UTF_8));
            }
        }
        return readResponse(conn);
    }

    private JSONObject readResponse(HttpURLConnection conn) throws Exception {
        int code = conn.getResponseCode();
        InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
        if (is == null) {
            throw new RuntimeException("DS API 返回空响应, HTTP " + code);
        }
        BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) {
            sb.append(line);
        }
        br.close();
        conn.disconnect();

        String body = sb.toString();
        log.debug("DS API 响应: {}", body);

        JSONObject result = JSON.parseObject(body);
        if (result == null) {
            throw new RuntimeException("DS API 返回非 JSON 内容: " + body);
        }
        return result;
    }

    private void checkSuccess(JSONObject result, String action) {
        int code = result.getIntValue("code");
        if (code != 0) {
            String msg = result.getString("msg");
            throw new RuntimeException(action + " 失败: " + msg + " (code=" + code + ")");
        }
    }

    // ======================== Task Code 生成 ========================

    /**
     * 生成唯一的 task code
     */
    public long genTaskCode() throws Exception {
        JSONObject result = doGet("/projects/" + projectCode + "/task-definition/gen-task-codes?genNum=1");
        checkSuccess(result, "生成 taskCode");
        JSONArray codes = result.getJSONArray("data");
        return codes.getLongValue(0);
    }

    // ======================== Workflow (Process Definition) ========================

    /**
     * 创建工作流（一个脚本 = 一个 workflow + 一个 task）
     */
    public JSONObject createWorkflow(String name, long taskCode, String scriptType, String scriptContent,
                                     int timeout, int retryTimes, int retryInterval) throws Exception {
        // 构建 taskDefinitionJson
        JSONObject taskDef = buildTaskDefinition(taskCode, name, scriptType, scriptContent,
                timeout, retryTimes, retryInterval);
        JSONArray taskDefArr = new JSONArray();
        taskDefArr.add(taskDef);

        // 构建 taskRelationJson（单任务，preTaskCode=0 表示入口节点）
        JSONObject relation = new JSONObject();
        relation.put("preTaskCode", 0);
        relation.put("preTaskVersion", 0);
        relation.put("postTaskCode", taskCode);
        relation.put("postTaskVersion", 1);
        relation.put("conditionType", "NONE");
        relation.put("conditionParams", new JSONObject());
        JSONArray relationArr = new JSONArray();
        relationArr.add(relation);

        Map<String, String> params = new LinkedHashMap<>();
        params.put("name", name);
        params.put("tenantCode", tenantCode);
        params.put("taskDefinitionJson", taskDefArr.toJSONString());
        params.put("taskRelationJson", relationArr.toJSONString());
        params.put("executionType", "PARALLEL");
        params.put("description", "DataNote 自动创建 - " + name);
        params.put("globalParams", "[]");
        params.put("timeout", "0");

        JSONObject result = doPost("/projects/" + projectCode + "/process-definition", params);
        checkSuccess(result, "创建工作流");
        log.info("创建工作流成功: {}", name);
        return result.getJSONObject("data");
    }

    /**
     * 更新工作流（更新脚本内容、超时、重试等）
     */
    public void updateWorkflow(long workflowCode, String name, long taskCode,
                               String scriptType, String scriptContent,
                               int timeout, int retryTimes, int retryInterval) throws Exception {
        JSONObject taskDef = buildTaskDefinition(taskCode, name, scriptType, scriptContent,
                timeout, retryTimes, retryInterval);
        JSONArray taskDefArr = new JSONArray();
        taskDefArr.add(taskDef);

        JSONObject relation = new JSONObject();
        relation.put("preTaskCode", 0);
        relation.put("preTaskVersion", 0);
        relation.put("postTaskCode", taskCode);
        relation.put("postTaskVersion", 1);
        relation.put("conditionType", "NONE");
        relation.put("conditionParams", new JSONObject());
        JSONArray relationArr = new JSONArray();
        relationArr.add(relation);

        Map<String, String> params = new LinkedHashMap<>();
        params.put("name", name);
        params.put("tenantCode", tenantCode);
        params.put("taskDefinitionJson", taskDefArr.toJSONString());
        params.put("taskRelationJson", relationArr.toJSONString());
        params.put("executionType", "PARALLEL");
        params.put("description", "DataNote 自动创建 - " + name);
        params.put("globalParams", "[]");
        params.put("timeout", "0");

        JSONObject result = doPut("/projects/" + projectCode + "/process-definition/" + workflowCode, params);
        checkSuccess(result, "更新工作流");
        log.info("更新工作流成功: {} (code={})", name, workflowCode);
    }

    /**
     * 上线工作流
     */
    public void releaseWorkflow(long workflowCode, boolean online) throws Exception {
        String state = online ? "ONLINE" : "OFFLINE";
        Map<String, String> params = new LinkedHashMap<>();
        params.put("releaseState", state);
        JSONObject result = doPost("/projects/" + projectCode + "/process-definition/" + workflowCode + "/release", params);
        checkSuccess(result, (online ? "上线" : "下线") + "工作流");
        log.info("工作流 {} 成功 (code={})", state, workflowCode);
    }

    // ======================== Schedule ========================

    /**
     * 创建调度
     */
    public int createSchedule(long workflowCode, String cron, String warningType) throws Exception {
        JSONObject scheduleJson = new JSONObject();
        scheduleJson.put("startTime", "2020-01-01 00:00:00");
        scheduleJson.put("endTime", "2099-12-31 23:59:59");
        scheduleJson.put("crontab", cron);
        scheduleJson.put("timezoneId", "Asia/Shanghai");

        Map<String, String> params = new LinkedHashMap<>();
        params.put("processDefinitionCode", String.valueOf(workflowCode));
        params.put("schedule", scheduleJson.toJSONString());
        params.put("warningType", warningType != null ? warningType : "NONE");
        params.put("warningGroupId", "0");
        params.put("failureStrategy", "CONTINUE");
        params.put("workerGroup", "default");
        params.put("processInstancePriority", "MEDIUM");

        JSONObject result = doPost("/projects/" + projectCode + "/schedules", params);
        checkSuccess(result, "创建调度");
        int scheduleId = result.getJSONObject("data").getIntValue("id");
        log.info("创建调度成功: scheduleId={}, cron={}", scheduleId, cron);
        return scheduleId;
    }

    /**
     * 更新调度
     */
    public void updateSchedule(int scheduleId, String cron, String warningType) throws Exception {
        JSONObject scheduleJson = new JSONObject();
        scheduleJson.put("startTime", "2020-01-01 00:00:00");
        scheduleJson.put("endTime", "2099-12-31 23:59:59");
        scheduleJson.put("crontab", cron);
        scheduleJson.put("timezoneId", "Asia/Shanghai");

        Map<String, String> params = new LinkedHashMap<>();
        params.put("schedule", scheduleJson.toJSONString());
        params.put("warningType", warningType != null ? warningType : "NONE");
        params.put("warningGroupId", "0");
        params.put("failureStrategy", "CONTINUE");
        params.put("workerGroup", "default");
        params.put("processInstancePriority", "MEDIUM");

        JSONObject result = doPut("/projects/" + projectCode + "/schedules/" + scheduleId, params);
        checkSuccess(result, "更新调度");
        log.info("更新调度成功: scheduleId={}, cron={}", scheduleId, cron);
    }

    /**
     * 上线调度
     */
    public void onlineSchedule(int scheduleId) throws Exception {
        JSONObject result = doPost("/projects/" + projectCode + "/schedules/" + scheduleId + "/online", null);
        checkSuccess(result, "上线调度");
        log.info("调度上线成功: scheduleId={}", scheduleId);
    }

    /**
     * 下线调度
     */
    public void offlineSchedule(int scheduleId) throws Exception {
        JSONObject result = doPost("/projects/" + projectCode + "/schedules/" + scheduleId + "/offline", null);
        checkSuccess(result, "下线调度");
        log.info("调度下线成功: scheduleId={}", scheduleId);
    }

    // ======================== 任务实例 & 日志 ========================

    /**
     * 查询任务实例列表（按工作流名称）
     */
    public JSONArray getTaskInstances(String workflowName, int pageNo, int pageSize) throws Exception {
        String path = "/projects/" + projectCode + "/task-instances"
                + "?processDefinitionName=" + URLEncoder.encode(workflowName, "UTF-8")
                + "&pageNo=" + pageNo + "&pageSize=" + pageSize;
        JSONObject result = doGet(path);
        checkSuccess(result, "查询任务实例");
        return result.getJSONObject("data").getJSONArray("totalList");
    }

    /**
     * 读取任务执行日志
     */
    public String getTaskLog(int taskInstanceId, int skipLineNum, int limit) throws Exception {
        String path = "/log/detail?taskInstanceId=" + taskInstanceId
                + "&skipLineNum=" + skipLineNum + "&limit=" + limit;
        JSONObject result = doGet(path);
        checkSuccess(result, "读取任务日志");
        return result.getString("data");
    }

    // ======================== 血缘 ========================

    /**
     * 查询单个工作流的血缘
     */
    public JSONObject getWorkflowLineage(long workflowCode) throws Exception {
        JSONObject result = doGet("/projects/" + projectCode + "/lineages/" + workflowCode);
        checkSuccess(result, "查询血缘");
        return result.getJSONObject("data");
    }

    /**
     * 查询项目所有工作流血缘
     */
    public JSONObject getAllLineage() throws Exception {
        JSONObject result = doGet("/projects/" + projectCode + "/lineages/list");
        checkSuccess(result, "查询全部血缘");
        return result.getJSONObject("data");
    }

    // ======================== 一键上线 / 下线 ========================

    /**
     * 一键上线：创建或更新 workflow → 上线 → 创建或更新 schedule → 激活
     * 返回更新后的 DS 关联信息
     */
    public Map<String, Object> onlineScript(String scriptName, String scriptType,
                                             String scriptContent,
                                             Long existingWorkflowCode, Long existingTaskCode,
                                             Integer existingScheduleId,
                                             String cron, int timeout,
                                             int retryTimes, int retryInterval,
                                             String warningType) throws Exception {
        Map<String, Object> result = new HashMap<>();
        long taskCode;
        long workflowCode;
        int scheduleId;

        if (existingWorkflowCode != null && existingWorkflowCode > 0) {
            // 已有 workflow，先下线再更新
            taskCode = existingTaskCode;
            workflowCode = existingWorkflowCode;
            try {
                releaseWorkflow(workflowCode, false);
            } catch (Exception e) {
                log.warn("下线旧工作流时出错（可能本来就是离线状态）: {}", e.getMessage());
            }
            // 下线旧调度
            if (existingScheduleId != null && existingScheduleId > 0) {
                try {
                    offlineSchedule(existingScheduleId);
                } catch (Exception e) {
                    log.warn("下线旧调度时出错: {}", e.getMessage());
                }
            }
            updateWorkflow(workflowCode, scriptName, taskCode, scriptType, scriptContent,
                    timeout, retryTimes, retryInterval);
        } else {
            // 新建
            taskCode = genTaskCode();
            JSONObject wf = createWorkflow(scriptName, taskCode, scriptType, scriptContent,
                    timeout, retryTimes, retryInterval);
            workflowCode = wf.getLongValue("code");
        }

        // 上线 workflow
        releaseWorkflow(workflowCode, true);

        // 创建或更新 schedule
        if (existingScheduleId != null && existingScheduleId > 0) {
            updateSchedule(existingScheduleId, cron, warningType);
            scheduleId = existingScheduleId;
        } else {
            scheduleId = createSchedule(workflowCode, cron, warningType);
        }

        // 激活 schedule
        onlineSchedule(scheduleId);

        result.put("dsTaskCode", taskCode);
        result.put("dsWorkflowCode", workflowCode);
        result.put("dsScheduleId", scheduleId);
        result.put("dsProjectCode", projectCode);
        return result;
    }

    /**
     * 一键下线：停止调度 → 下线 workflow
     */
    public void offlineScript(long workflowCode, Integer scheduleId) throws Exception {
        if (scheduleId != null && scheduleId > 0) {
            try {
                offlineSchedule(scheduleId);
            } catch (Exception e) {
                log.warn("下线调度时出错: {}", e.getMessage());
            }
        }
        releaseWorkflow(workflowCode, false);
        log.info("脚本下线成功: workflowCode={}", workflowCode);
    }

    // ======================== 内部方法 ========================

    private JSONObject buildTaskDefinition(long taskCode, String name, String scriptType,
                                           String scriptContent,
                                           int timeout, int retryTimes, int retryInterval) {
        JSONObject taskDef = new JSONObject();
        taskDef.put("code", taskCode);
        taskDef.put("name", name);
        taskDef.put("flag", "YES");
        taskDef.put("taskPriority", "MEDIUM");
        taskDef.put("workerGroup", "default");
        taskDef.put("failRetryTimes", retryTimes);
        taskDef.put("failRetryInterval", retryInterval);
        taskDef.put("delayTime", 0);
        taskDef.put("environmentCode", -1);

        if (timeout > 0) {
            taskDef.put("timeoutFlag", "OPEN");
            taskDef.put("timeoutNotifyStrategy", "WARN");
            taskDef.put("timeout", timeout / 60); // DS 的 timeout 单位是分钟
        } else {
            taskDef.put("timeoutFlag", "CLOSE");
            taskDef.put("timeout", 0);
        }

        // 根据脚本类型构建 taskParams
        JSONObject taskParams = new JSONObject();
        String taskType;

        switch (scriptType.toLowerCase()) {
            case "shell":
                taskType = "SHELL";
                taskParams.put("rawScript", scriptContent);
                taskParams.put("localParams", new JSONArray());
                taskParams.put("resourceList", new JSONArray());
                break;
            case "python":
                taskType = "PYTHON";
                taskParams.put("rawScript", scriptContent);
                taskParams.put("localParams", new JSONArray());
                taskParams.put("resourceList", new JSONArray());
                break;
            case "hive":
            case "sql":
            default:
                // HiveSQL 统一用 SHELL 类型，通过 hive -e 执行，避免依赖 DS 数据源配置
                taskType = "SHELL";
                String shellScript = "#!/bin/bash\n"
                        + "# DataNote HiveSQL: " + name + "\n"
                        + "hive -e \"$(cat <<'HIVESQL'\n"
                        + scriptContent + "\n"
                        + "HIVESQL\n"
                        + ")\"";
                taskParams.put("rawScript", shellScript);
                taskParams.put("localParams", new JSONArray());
                taskParams.put("resourceList", new JSONArray());
                break;
        }

        taskDef.put("taskType", taskType);
        taskDef.put("taskParams", taskParams.toJSONString()); // DS 3.x 要求 taskParams 为 JSON 字符串
        return taskDef;
    }
}
