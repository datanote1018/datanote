package com.datanote.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.datanote.exception.ResourceNotFoundException;
import com.datanote.mapper.DnQualityRuleMapper;
import com.datanote.mapper.DnQualityRunMapper;
import com.datanote.model.DnQualityRule;
import com.datanote.model.DnQualityRun;
import com.datanote.model.R;
import com.datanote.service.QualityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据质量管理 Controller
 */
@RestController
@RequestMapping("/api/quality")
@Tag(name = "数据质量管理", description = "质量规则的增删改查、执行检查、结果查询")
@RequiredArgsConstructor
public class QualityController {

    private final DnQualityRuleMapper ruleMapper;
    private final DnQualityRunMapper runMapper;
    private final QualityService qualityService;

    /**
     * 获取所有质量规则
     */
    @Operation(summary = "质量规则列表")
    @GetMapping("/rules")
    public R<List<DnQualityRule>> listRules() {
        QueryWrapper<DnQualityRule> qw = new QueryWrapper<>();
        qw.orderByDesc("updated_at");
        return R.ok(ruleMapper.selectList(qw));
    }

    /**
     * 根据 ID 获取质量规则
     */
    @Operation(summary = "查询质量规则详情")
    @GetMapping("/rule/{id}")
    public R<DnQualityRule> getRule(@PathVariable Long id) {
        DnQualityRule rule = ruleMapper.selectById(id);
        if (rule == null) {
            throw new ResourceNotFoundException("质量规则");
        }
        return R.ok(rule);
    }

    /**
     * 保存质量规则（新增或更新）
     */
    @Operation(summary = "保存质量规则")
    @PostMapping("/rule/save")
    public R<DnQualityRule> saveRule(@RequestBody DnQualityRule rule) {
        if (rule.getId() != null) {
            rule.setUpdatedAt(LocalDateTime.now());
            ruleMapper.updateById(rule);
        } else {
            rule.setCreatedAt(LocalDateTime.now());
            rule.setUpdatedAt(LocalDateTime.now());
            if (rule.getStatus() == null) {
                rule.setStatus(1);
            }
            ruleMapper.insert(rule);
        }
        return R.ok(rule);
    }

    /**
     * 删除质量规则
     */
    @Operation(summary = "删除质量规则")
    @DeleteMapping("/rule/{id}")
    public R<String> deleteRule(@PathVariable Long id) {
        ruleMapper.deleteById(id);
        return R.ok("删除成功");
    }

    /**
     * 手动执行质量检查
     */
    @Operation(summary = "执行质量检查")
    @PostMapping("/rule/{id}/run")
    public R<DnQualityRun> runRule(@PathVariable Long id) {
        DnQualityRule rule = ruleMapper.selectById(id);
        if (rule == null) {
            throw new ResourceNotFoundException("质量规则");
        }
        DnQualityRun result = qualityService.executeRule(rule);
        return R.ok(result);
    }

    /**
     * 批量执行所有启用的质量规则
     */
    @Transactional(rollbackFor = Exception.class)
    @Operation(summary = "批量执行质量检查")
    @PostMapping("/run-all")
    public R<String> runAll() {
        QueryWrapper<DnQualityRule> qw = new QueryWrapper<>();
        qw.eq("status", 1);
        List<DnQualityRule> rules = ruleMapper.selectList(qw);
        int count = 0;
        for (DnQualityRule rule : rules) {
            qualityService.executeRule(rule);
            count++;
        }
        return R.ok("已执行 " + count + " 条规则");
    }

    /**
     * 获取指定规则的执行历史
     */
    @Operation(summary = "获取规则执行历史")
    @GetMapping("/rule/{id}/runs")
    public R<List<DnQualityRun>> listRuns(@PathVariable Long id) {
        QueryWrapper<DnQualityRun> qw = new QueryWrapper<>();
        qw.eq("rule_id", id).orderByDesc("started_at").last("LIMIT 20");
        return R.ok(runMapper.selectList(qw));
    }

    /**
     * 获取质量概览统计
     */
    @Operation(summary = "质量概览统计")
    @GetMapping("/overview")
    public R<Map<String, Object>> overview() {
        Map<String, Object> data = new HashMap<>();

        long totalRules = ruleMapper.selectCount(null);
        QueryWrapper<DnQualityRule> enabledQw = new QueryWrapper<>();
        enabledQw.eq("status", 1);
        long enabledRules = ruleMapper.selectCount(enabledQw);

        data.put("totalRules", totalRules);
        data.put("enabledRules", enabledRules);

        // 最近24小时的执行统计
        QueryWrapper<DnQualityRun> recentQw = new QueryWrapper<>();
        recentQw.ge("started_at", LocalDateTime.now().minusHours(24));
        List<DnQualityRun> recentRuns = runMapper.selectList(recentQw);

        long totalRuns = recentRuns.size();
        long successRuns = recentRuns.stream().filter(r -> "success".equals(r.getRunStatus())).count();
        long failedRuns = recentRuns.stream().filter(r -> "failed".equals(r.getRunStatus())).count();
        long errorRuns = recentRuns.stream().filter(r -> "error".equals(r.getRunStatus())).count();

        data.put("totalRuns", totalRuns);
        data.put("successRuns", successRuns);
        data.put("failedRuns", failedRuns);
        data.put("errorRuns", errorRuns);

        return R.ok(data);
    }
}
