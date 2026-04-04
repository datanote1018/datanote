package com.datanote.controller;

import com.alibaba.fastjson.JSONObject;
import com.datanote.exception.ResourceNotFoundException;
import com.datanote.model.*;
import com.datanote.service.DolphinService;
import com.datanote.service.ScriptService;
import com.datanote.service.TaskDependencyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 血缘关系 Controller — 参数校验 + 调用 Service
 */
@Slf4j
@RestController
@RequestMapping("/api/lineage")
@RequiredArgsConstructor
@Tag(name = "血缘关系", description = "血缘关系查询、下游依赖树、依赖刷新")
public class LineageController {

    private final DolphinService dolphinService;
    private final ScriptService scriptService;
    private final TaskDependencyService taskDependencyService;

    @GetMapping("/{scriptId}")
    @Operation(summary = "查询脚本血缘关系")
    public R<JSONObject> getLineage(@PathVariable Long scriptId) {
        try {
            DnScript script = scriptService.getById(scriptId);
            if (script == null) throw new ResourceNotFoundException("脚本");
            if (script.getDsWorkflowCode() == null || script.getDsWorkflowCode() == 0) {
                return R.ok(new JSONObject());
            }
            return R.ok(dolphinService.getWorkflowLineage(script.getDsWorkflowCode()));
        } catch (ResourceNotFoundException e) {
            throw e;
        } catch (Exception e) {
            log.error("查询血缘失败: scriptId={}", scriptId, e);
            return R.fail("查询血缘失败");
        }
    }

    @GetMapping("/all")
    @Operation(summary = "查询项目全部血缘关系")
    public R<JSONObject> getAllLineage() {
        try {
            return R.ok(dolphinService.getAllLineage());
        } catch (Exception e) {
            log.error("查询全部血缘失败", e);
            return R.fail("查询血缘失败");
        }
    }

    @GetMapping("/downstream-tree")
    @Operation(summary = "获取下游依赖树")
    public R<List<Map<String, Object>>> getDownstreamTree(
            @RequestParam Long taskId, @RequestParam String taskType) {
        return R.ok(taskDependencyService.getDownstreamTree(taskId, taskType));
    }

    @PostMapping("/refresh-deps")
    @Operation(summary = "刷新所有依赖关系")
    public R<Map<String, Object>> refreshDeps() {
        int count = taskDependencyService.refreshAllDependencies();
        Map<String, Object> result = new HashMap<>();
        result.put("dependencyCount", count);
        return R.ok(result);
    }

    @GetMapping("/search-tasks")
    @Operation(summary = "搜索在线任务（用于手动添加依赖）")
    public R<List<Map<String, Object>>> searchTasks(@RequestParam String keyword) {
        return R.ok(taskDependencyService.searchOnlineTasks(keyword));
    }

    @PostMapping("/add-dep")
    @Operation(summary = "手动添加依赖")
    public R<String> addDependency(@RequestBody Map<String, Object> body) {
        Long taskId = Long.valueOf(body.get("taskId").toString());
        String taskType = (String) body.get("taskType");
        Long upstreamTaskId = Long.valueOf(body.get("upstreamTaskId").toString());
        String upstreamTaskType = (String) body.get("upstreamTaskType");
        String depTable = body.get("depTable") != null ? body.get("depTable").toString() : null;

        boolean added = taskDependencyService.addManualDependency(taskId, taskType, upstreamTaskId, upstreamTaskType, depTable);
        return added ? R.ok("添加成功") : R.fail("该依赖关系已存在");
    }

    @DeleteMapping("/dep/{id}")
    @Operation(summary = "删除依赖")
    public R<String> deleteDependency(@PathVariable Long id) {
        taskDependencyService.deleteDependency(id);
        return R.ok("删除成功");
    }

    @GetMapping("/deps")
    @Operation(summary = "查询任务依赖列表")
    public R<List<DnTaskDependency>> listDeps(@RequestParam Long taskId, @RequestParam String taskType) {
        return R.ok(taskDependencyService.listDependencies(taskId, taskType));
    }
}
