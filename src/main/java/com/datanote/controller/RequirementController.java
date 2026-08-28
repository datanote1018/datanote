package com.datanote.controller;

import com.datanote.model.DnRequirement;
import com.datanote.model.DnRequirementLog;
import com.datanote.model.R;
import com.datanote.service.RequirementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 取数需求工作项控制器 — 需求的增删改查、状态流转、口径/SQL/结果保存
 */
@Tag(name = "需求管理")
@RestController
@RequestMapping("/api/requirements")
@RequiredArgsConstructor
public class RequirementController {

    private final RequirementService service;

    @Operation(summary = "需求列表")
    @GetMapping
    public R<List<DnRequirement>> list() {
        return R.ok(service.list());
    }

    @Operation(summary = "需求详情")
    @GetMapping("/{id}")
    public R<DnRequirement> get(@PathVariable Long id) {
        return R.ok(service.get(id));
    }

    @Operation(summary = "流转记录")
    @GetMapping("/{id}/logs")
    public R<List<DnRequirementLog>> logs(@PathVariable Long id) {
        return R.ok(service.logs(id));
    }

    @Operation(summary = "新建需求")
    @PostMapping
    public R<DnRequirement> create(@RequestBody DnRequirement body) {
        try {
            return R.ok(service.create(body));
        } catch (Exception e) {
            return R.fail(e.getMessage());
        }
    }

    @Operation(summary = "状态流转")
    @PutMapping("/{id}/status")
    public R<Void> updateStatus(@PathVariable Long id, @RequestBody StatusForm f) {
        try {
            service.updateStatus(id, f.getStatus(), f.getOperator(), f.getComment());
            return R.ok();
        } catch (Exception e) {
            return R.fail(e.getMessage());
        }
    }

    @Operation(summary = "保存加工口径")
    @PutMapping("/{id}/spec")
    public R<Void> saveSpec(@PathVariable Long id, @RequestBody SpecForm f) {
        try {
            service.saveSpec(id, f.getSpec());
            return R.ok();
        } catch (Exception e) {
            return R.fail(e.getMessage());
        }
    }

    @Operation(summary = "保存出数结果")
    @PutMapping("/{id}/query")
    public R<Void> saveQuery(@PathVariable Long id, @RequestBody QueryForm f) {
        try {
            service.saveQuery(id, f.getSqlText(), f.getResultSummary());
            return R.ok();
        } catch (Exception e) {
            return R.fail(e.getMessage());
        }
    }

    @Operation(summary = "保存核对结论")
    @PutMapping("/{id}/check")
    public R<Void> saveCheck(@PathVariable Long id, @RequestBody CheckForm f) {
        try {
            service.saveCheck(id, f.getCheckResult());
            return R.ok();
        } catch (Exception e) {
            return R.fail(e.getMessage());
        }
    }

    @Operation(summary = "删除需求")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return R.ok();
    }

    @Data
    public static class StatusForm {
        private String status;
        private String operator;
        private String comment;
    }

    @Data
    public static class SpecForm {
        private String spec;
    }

    @Data
    public static class QueryForm {
        private String sqlText;
        private String resultSummary;
    }

    @Data
    public static class CheckForm {
        private String checkResult;
    }
}
