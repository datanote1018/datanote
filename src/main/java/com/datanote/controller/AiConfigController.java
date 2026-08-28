package com.datanote.controller;

import com.datanote.model.DnAiConfig;
import com.datanote.model.R;
import com.datanote.service.AiAssistService;
import com.datanote.service.AiConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * AI 模型配置控制器 — 多套配置的增删改查、默认切换、连接测试
 */
@Tag(name = "AI 配置管理")
@RestController
@RequestMapping("/api/ai/configs")
@RequiredArgsConstructor
public class AiConfigController {

    private final AiConfigService aiConfigService;
    private final AiAssistService aiAssistService;

    @Operation(summary = "配置列表")
    @GetMapping
    public R<List<DnAiConfig>> list() {
        return R.ok(aiConfigService.list());
    }

    @Operation(summary = "新建配置")
    @PostMapping
    public R<DnAiConfig> create(@RequestBody DnAiConfig body) {
        try {
            return R.ok(aiConfigService.create(body));
        } catch (Exception e) {
            return R.fail(e.getMessage());
        }
    }

    @Operation(summary = "编辑配置")
    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody DnAiConfig body) {
        try {
            aiConfigService.update(id, body);
            return R.ok();
        } catch (Exception e) {
            return R.fail(e.getMessage());
        }
    }

    @Operation(summary = "删除配置")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        aiConfigService.delete(id);
        return R.ok();
    }

    @Operation(summary = "设为默认")
    @PutMapping("/{id}/default")
    public R<Void> setDefault(@PathVariable Long id) {
        aiConfigService.setDefault(id);
        return R.ok();
    }

    @Operation(summary = "测试连接")
    @PostMapping("/test")
    public R<Void> test(@RequestBody DnAiConfig body) {
        String key = aiConfigService.resolveTestKey(body);
        boolean ok = aiAssistService.testConnection(body.getProvider(), key, body.getBaseUrl(), body.getModel());
        return ok ? R.ok() : R.fail("连接失败，请检查 API Key 和 Base URL");
    }
}
