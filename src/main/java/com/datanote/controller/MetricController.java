package com.datanote.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.datanote.exception.ResourceNotFoundException;
import com.datanote.mapper.DnMetricMapper;
import com.datanote.model.DnMetric;
import com.datanote.model.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 指标管理 Controller
 */
@RestController
@RequestMapping("/api/metric")
@Tag(name = "指标管理", description = "业务指标的定义、分类、搜索")
@RequiredArgsConstructor
public class MetricController {

    private final DnMetricMapper metricMapper;

    /**
     * 指标列表（支持关键词搜索和分类筛选）
     */
    @Operation(summary = "指标列表")
    @GetMapping("/list")
    public R<List<DnMetric>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category) {
        QueryWrapper<DnMetric> qw = new QueryWrapper<>();
        if (keyword != null && !keyword.isEmpty()) {
            qw.and(w -> w.like("metric_name", keyword)
                    .or().like("metric_code", keyword)
                    .or().like("description", keyword));
        }
        if (category != null && !category.isEmpty()) {
            qw.eq("category", category);
        }
        qw.orderByDesc("updated_at");
        return R.ok(metricMapper.selectList(qw));
    }

    /**
     * 指标详情
     */
    @Operation(summary = "指标详情")
    @GetMapping("/{id}")
    public R<DnMetric> getById(@PathVariable Long id) {
        DnMetric metric = metricMapper.selectById(id);
        if (metric == null) {
            throw new ResourceNotFoundException("指标");
        }
        return R.ok(metric);
    }

    /**
     * 保存指标
     */
    @Operation(summary = "保存指标")
    @PostMapping("/save")
    public R<DnMetric> save(@RequestBody DnMetric metric) {
        if (metric.getId() != null) {
            metric.setUpdatedAt(LocalDateTime.now());
            metricMapper.updateById(metric);
        } else {
            metric.setCreatedAt(LocalDateTime.now());
            metric.setUpdatedAt(LocalDateTime.now());
            if (metric.getStatus() == null) {
                metric.setStatus(1);
            }
            metricMapper.insert(metric);
        }
        return R.ok(metric);
    }

    /**
     * 删除指标
     */
    @Operation(summary = "删除指标")
    @DeleteMapping("/{id}")
    public R<String> delete(@PathVariable Long id) {
        metricMapper.deleteById(id);
        return R.ok("删除成功");
    }

    /**
     * 获取所有指标分类
     */
    @Operation(summary = "指标分类列表")
    @GetMapping("/categories")
    public R<List<String>> categories() {
        List<DnMetric> all = metricMapper.selectList(null);
        List<String> cats = all.stream()
                .map(DnMetric::getCategory)
                .filter(c -> c != null && !c.isEmpty())
                .distinct()
                .sorted()
                .collect(Collectors.toList());
        return R.ok(cats);
    }

    /**
     * 指标统计概览
     */
    @Operation(summary = "指标统计")
    @GetMapping("/stats")
    public R<Map<String, Object>> stats() {
        Map<String, Object> data = new HashMap<>();
        data.put("totalMetrics", metricMapper.selectCount(null));
        QueryWrapper<DnMetric> activeQw = new QueryWrapper<>();
        activeQw.eq("status", 1);
        data.put("activeMetrics", metricMapper.selectCount(activeQw));
        return R.ok(data);
    }
}
