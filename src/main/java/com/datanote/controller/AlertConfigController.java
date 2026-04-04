package com.datanote.controller;

import com.datanote.model.DnAlertConfig;
import com.datanote.model.R;
import com.datanote.service.AlertConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 告警配置管理 Controller
 */
@RestController
@RequestMapping("/api/alert-config")
@RequiredArgsConstructor
@Tag(name = "告警配置管理", description = "脚本告警规则的查询、保存与阈值自动计算")
public class AlertConfigController {

    private final AlertConfigService alertConfigService;

    /**
     * 获取指定脚本的告警配置
     */
    @Operation(summary = "获取脚本告警配置")
    @GetMapping("/{scriptId}")
    public R<DnAlertConfig> getByScriptId(@PathVariable Long scriptId) {
        return R.ok(alertConfigService.getByScriptId(scriptId));
    }

    /**
     * 保存告警配置
     */
    @Operation(summary = "保存告警配置")
    @PostMapping
    public R<DnAlertConfig> save(@RequestBody DnAlertConfig config) {
        return R.ok(alertConfigService.save(config));
    }

    /**
     * 自动计算延迟阈值
     */
    @Operation(summary = "自动计算延迟阈值")
    @GetMapping("/{scriptId}/delay-threshold")
    public R<Map<String, Object>> calculateDelayThreshold(@PathVariable Long scriptId) {
        int threshold = alertConfigService.calculateDelayThreshold(scriptId);
        Map<String, Object> result = new HashMap<>();
        result.put("scriptId", scriptId);
        result.put("delayThresholdMin", threshold);
        return R.ok(result);
    }
}
