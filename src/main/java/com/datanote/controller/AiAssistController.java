package com.datanote.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.datanote.mapper.DnScriptMapper;
import com.datanote.mapper.DnSystemConfigMapper;
import com.datanote.model.DnScript;
import com.datanote.model.DnSystemConfig;
import com.datanote.model.R;
import com.datanote.model.dto.AiChatRequest;
import com.datanote.model.dto.AiNl2SqlRequest;
import com.datanote.model.dto.AiSqlRequest;
import com.datanote.model.dto.GenerateTableNameRequest;
import com.datanote.service.AiAssistService;
import com.datanote.util.CryptoUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * AI 辅助开发 Controller
 */
@RestController
@RequestMapping("/api/ai")
@Tag(name = "AI 辅助开发", description = "NL2SQL、SQL解释、SQL优化等AI功能")
@RequiredArgsConstructor
public class AiAssistController {

    private final AiAssistService aiAssistService;
    private final DnScriptMapper scriptMapper;
    private final DnSystemConfigMapper systemConfigMapper;

    @Value("${datanote.crypto.key:}")
    private String cryptoKey;

    /**
     * AI 对话
     */
    @Operation(summary = "AI 对话")
    @PostMapping("/chat")
    public R<Map<String, String>> chat(@RequestBody AiChatRequest body) {
        String message = body.getMessage();
        String context = body.getContext();
        if (message == null || message.trim().isEmpty()) {
            return R.fail("消息不能为空");
        }
        String reply = aiAssistService.chat(message, context);
        Map<String, String> result = new HashMap<>();
        result.put("reply", reply);
        return R.ok(result);
    }

    /**
     * 自然语言转 SQL
     */
    @Operation(summary = "自然语言转SQL")
    @PostMapping("/nl2sql")
    public R<Map<String, String>> nl2sql(@RequestBody AiNl2SqlRequest body) {
        String question = body.getQuestion();
        String tableSchema = body.getTableSchema() != null ? body.getTableSchema() : "";
        if (question == null || question.trim().isEmpty()) {
            return R.fail("问题不能为空");
        }
        String reply = aiAssistService.nl2sql(question, tableSchema);
        Map<String, String> result = new HashMap<>();
        result.put("reply", reply);
        // 提取 SQL 代码块
        String sql = extractSqlBlock(reply);
        if (sql != null) {
            result.put("sql", sql);
        }
        return R.ok(result);
    }

    /**
     * SQL 解释
     */
    @Operation(summary = "SQL解释")
    @PostMapping("/explain")
    public R<Map<String, String>> explainSql(@RequestBody AiSqlRequest body) {
        String sql = body.getSql();
        if (sql == null || sql.trim().isEmpty()) {
            return R.fail("SQL不能为空");
        }
        String reply = aiAssistService.explainSql(sql);
        Map<String, String> result = new HashMap<>();
        result.put("reply", reply);
        return R.ok(result);
    }

    /**
     * SQL 优化
     */
    @Operation(summary = "SQL优化建议")
    @PostMapping("/optimize")
    public R<Map<String, String>> optimizeSql(@RequestBody AiSqlRequest body) {
        String sql = body.getSql();
        if (sql == null || sql.trim().isEmpty()) {
            return R.fail("SQL不能为空");
        }
        String reply = aiAssistService.optimizeSql(sql);
        Map<String, String> result = new HashMap<>();
        result.put("reply", reply);
        return R.ok(result);
    }

    /**
     * 检查 AI 功能状态
     */
    @Operation(summary = "AI功能状态")
    @GetMapping("/status")
    public R<Map<String, Object>> status() {
        Map<String, Object> result = new HashMap<>();
        result.put("available", aiAssistService.isAvailable());
        return R.ok(result);
    }

    /**
     * 获取 AI 配置（API Key 脱敏）
     */
    @Operation(summary = "获取AI配置")
    @GetMapping("/config")
    public R<Map<String, String>> getAiConfig() {
        Map<String, String> cfg = new HashMap<>();
        cfg.put("provider", getConfigValue("ai.provider", "anthropic"));
        cfg.put("baseUrl", getConfigValue("ai.base-url", ""));
        cfg.put("model", getConfigValue("ai.model", ""));
        String key = getConfigValue("ai.api-key", "");
        cfg.put("apiKeyMasked", key.isEmpty() ? "" : key.substring(0, Math.min(8, key.length())) + "***");
        return R.ok(cfg);
    }

    /**
     * 保存 AI 配置
     */
    @Operation(summary = "保存AI配置")
    @PostMapping("/config")
    public R<Void> saveAiConfig(@RequestBody Map<String, String> body) {
        saveConfigValue("ai.provider", body.get("provider"), "AI Provider");
        saveConfigValue("ai.base-url", body.get("baseUrl"), "API Base URL");
        saveConfigValue("ai.model", body.get("model"), "AI Model");
        if (body.get("apiKey") != null && !body.get("apiKey").contains("***")) {
            // 加密存储 API Key
            String encrypted = CryptoUtil.encrypt(body.get("apiKey"), cryptoKey);
            saveConfigValue("ai.api-key", encrypted != null ? encrypted : body.get("apiKey"), "API Key (encrypted)");
        }
        // 通知 AiAssistService 重新加载配置
        aiAssistService.reloadConfig();
        return R.ok();
    }

    /**
     * 测试 AI 连接
     */
    @Operation(summary = "测试AI连接")
    @PostMapping("/test-connection")
    public R<Void> testAiConnection(@RequestBody Map<String, String> body) {
        String key = body.get("apiKey");
        if (key != null && key.contains("***")) {
            key = getConfigValue("ai.api-key", "");
        }
        if (key == null || key.isEmpty()) {
            return R.fail("API Key 不能为空");
        }
        try {
            boolean ok = aiAssistService.testConnection(
                    body.get("provider"), key, body.get("baseUrl"), body.get("model"));
            return ok ? R.ok() : R.fail("连接失败，请检查 API Key 和 Base URL");
        } catch (Exception e) {
            return R.fail("连接异常: " + e.getMessage());
        }
    }

    private String getConfigValue(String key, String defaultValue) {
        DnSystemConfig cfg = systemConfigMapper.selectById(key);
        if (cfg == null || cfg.getConfigValue() == null) return defaultValue;
        // API Key 需要解密
        if (key.equals("ai.api-key")) {
            String decrypted = CryptoUtil.decrypt(cfg.getConfigValue(), cryptoKey);
            return decrypted != null ? decrypted : cfg.getConfigValue();
        }
        return cfg.getConfigValue();
    }

    private void saveConfigValue(String key, String value, String desc) {
        if (value == null) return;
        DnSystemConfig existing = systemConfigMapper.selectById(key);
        DnSystemConfig cfg = new DnSystemConfig();
        cfg.setConfigKey(key);
        cfg.setConfigValue(value);
        cfg.setDescription(desc);
        cfg.setUpdatedAt(LocalDateTime.now());
        if (existing != null) {
            systemConfigMapper.updateById(cfg);
        } else {
            systemConfigMapper.insert(cfg);
        }
    }

    /**
     * AI 生成标准化表名
     */
    @Operation(summary = "AI生成标准化表名")
    @PostMapping("/generate-table-name")
    public R<Map<String, Object>> generateTableName(@RequestBody GenerateTableNameRequest req) {
        String prompt = "你是一个数据仓库命名规范专家。请根据以下信息生成一个标准化的Hive表名：\n"
                + "- 数仓分层：" + req.getLayer() + "\n"
                + "- 表类型：" + req.getTableType() + "\n"
                + "- 主题域：" + req.getSubject() + "\n"
                + "- 二级主题：" + (req.getSubSubject() != null ? req.getSubSubject() : "无") + "\n"
                + "- 模型描述：" + req.getDescription() + "\n"
                + "- 数据库名：" + (req.getDbName() != null ? req.getDbName() : "default") + "\n\n"
                + "命名规范：{分层}_{库名}_{主题}_{描述}_{full/incr}\n"
                + "例如：dwd_mall_trade_order_detail_full\n\n"
                + "请只返回一个表名，不要其他解释。表名全部小写，用下划线连接。";

        String tableName = aiAssistService.chat(prompt, null);
        tableName = tableName.trim().toLowerCase().replaceAll("[^a-z0-9_]", "");

        // Check uniqueness
        QueryWrapper<DnScript> qw = new QueryWrapper<>();
        qw.eq("script_name", tableName);
        boolean exists = scriptMapper.selectCount(qw) > 0;

        Map<String, Object> result = new HashMap<>();
        result.put("tableName", tableName);
        result.put("exists", exists);
        return R.ok(result);
    }

    /**
     * AI 生成 Cron 表达式
     */
    @Operation(summary = "AI生成Cron表达式")
    @PostMapping("/generate-cron")
    public R<Map<String, String>> generateCron(@RequestBody AiChatRequest body) {
        String prompt = "你是一个 cron 表达式专家。用户的需求是：" + body.getMessage() + "\n\n"
                + "请生成一个 Spring Cron 表达式（6位，格式：秒 分 时 日 月 周）。\n"
                + "只返回 cron 表达式本身，不要其他解释。\n"
                + "例如：0 0 2 * * ? 表示每天凌晨2点执行";
        String reply = aiAssistService.chat(prompt, null);
        String cron = reply.trim().replaceAll("[^0-9* ?/,-]", " ").trim();
        Map<String, String> result = new HashMap<>();
        result.put("cron", cron);
        result.put("raw", reply);
        return R.ok(result);
    }

    private String extractSqlBlock(String text) {
        int start = text.indexOf("```sql");
        if (start == -1) start = text.indexOf("```SQL");
        if (start == -1) return null;
        start = text.indexOf('\n', start);
        if (start == -1) return null;
        int end = text.indexOf("```", start + 1);
        if (end == -1) return null;
        return text.substring(start + 1, end).trim();
    }
}
