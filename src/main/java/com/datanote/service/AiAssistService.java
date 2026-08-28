package com.datanote.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.datanote.mapper.DnSystemConfigMapper;
import com.datanote.model.DnSystemConfig;
import com.datanote.model.dto.GenerateTableNameRequest;
import com.datanote.util.CryptoUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * AI 辅助开发服务 — 调用 Claude API 实现 NL2SQL、SQL 解释等智能功能
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiAssistService {

    private final ObjectMapper objectMapper;
    private final DnSystemConfigMapper systemConfigMapper;

    @Value("${datanote.ai.api-key:}")
    private String envApiKey;

    @Value("${datanote.ai.model:claude-sonnet-4-6}")
    private String envModel;

    @Value("${datanote.ai.base-url:https://api.anthropic.com}")
    private String envBaseUrl;

    @Value("${datanote.crypto.key:}")
    private String cryptoKey;

    @Value("${datanote.ai.agent-url:http://localhost:8000}")
    private String agentUrl;

    // 运行时配置（数据库优先，环境变量兜底）
    private String apiKey;
    private String model;
    private String baseUrl;
    private String provider;  // anthropic / openai / deepseek / bailian / custom

    @PostConstruct
    public void reloadConfig() {
        // API Key 只来自环境变量（ai-service/.env）。
        // 不再从数据库读取和解密：密钥留在本机文件里，既不入库也不入仓库，
        // 也就不需要维护那把 AES crypto key。Python 侧 ai-service 读的是同一份 .env。
        this.apiKey = envApiKey;
        if (this.apiKey == null || this.apiKey.isEmpty()) {
            log.warn("未配置 AI API Key —— 请在 ai-service/.env 中设置 QWEN_API_KEY 后重启");
        }
        String dbModel = getDbConfig("ai.model");
        this.model = (dbModel != null && !dbModel.isEmpty()) ? dbModel : envModel;
        String dbUrl = getDbConfig("ai.base-url");
        this.baseUrl = (dbUrl != null && !dbUrl.isEmpty()) ? dbUrl : envBaseUrl;
        String dbProvider = getDbConfig("ai.provider");
        this.provider = (dbProvider != null && !dbProvider.isEmpty()) ? dbProvider : "anthropic";
        log.info("AI config loaded: provider={}, model={}, baseUrl={}, keyConfigured={}", provider, model, baseUrl, apiKey != null && !apiKey.isEmpty());
    }

    /** 当前生效的 API Key（来自 ai-service/.env）。仅供服务端内部使用，不对外返回。 */
    public String getApiKey() {
        return apiKey;
    }

    /** 是否已配置 API Key —— 页面用它显示配置状态，不暴露密钥本身。 */
    public boolean isApiKeyConfigured() {
        return apiKey != null && !apiKey.isEmpty();
    }

    /**
     * 判断是否使用 OpenAI 兼容格式（百炼/OpenAI/DeepSeek 都走这个格式）
     */
    private boolean isOpenAiCompatible(String p) {
        return "openai".equals(p) || "deepseek".equals(p) || "bailian".equals(p) || "custom".equals(p);
    }

    private String getDbConfig(String key) {
        try {
            DnSystemConfig cfg = systemConfigMapper.selectById(key);
            return cfg != null ? cfg.getConfigValue() : null;
        } catch (Exception e) {
            return null;  // 表不存在等情况，静默降级
        }
    }

    private static final String SYSTEM_PROMPT =
            "你是一个专业的数据工程师 AI 助手，专注于 SQL 开发和数据分析。\n" +
            "你的职责：\n" +
            "1. 将自然语言需求转换为准确的 SQL 语句\n" +
            "2. 解释复杂的 SQL 语句含义\n" +
            "3. 优化 SQL 性能\n" +
            "4. 回答数据工程相关问题\n\n" +
            "规则：\n" +
            "- 默认使用 HiveSQL 语法（支持分区表、ORC 格式等）\n" +
            "- SQL 语句用 ```sql 代码块包裹\n" +
            "- 回答简洁专业，中文回复\n" +
            "- 默认只回答和用户问题直接相关的内容，控制在 3-6 行\n" +
            "- 不主动展开背景、步骤、下一步建议、长表格，除非用户明确要求";

    /**
     * 调用 Claude API 进行对话
     *
     * @param userMessage 用户消息
     * @param context     上下文信息（如表结构、历史对话等）
     * @return AI 回复文本
     */
    public String chat(String userMessage, String context) {
        String agentReply = callAssistAgent("chat", userMessage, context);
        if (agentReply != null && !agentReply.isEmpty()) {
            return agentReply;
        }

        if (apiKey == null || apiKey.isEmpty()) {
            return "AI 功能未配置。请在【系统配置 → AI 配置】中设置 API Key。";
        }

        try {
            String fullMessage = userMessage;
            if (context != null && !context.isEmpty()) {
                fullMessage = "当前上下文：\n" + context + "\n\n用户问题：" + userMessage;
            }

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", model);

            List<Map<String, String>> messages = new ArrayList<>();
            if (isOpenAiCompatible(provider)) {
                // OpenAI 兼容格式（百炼/OpenAI/DeepSeek）：system 作为 message
                requestBody.put("max_tokens", 4096);
                Map<String, String> sysMsg = new HashMap<>();
                sysMsg.put("role", "system");
                sysMsg.put("content", SYSTEM_PROMPT);
                messages.add(sysMsg);
            } else {
                // Anthropic 格式：system 是顶层字段
                requestBody.put("max_tokens", 4096);
                requestBody.put("system", SYSTEM_PROMPT);
            }
            Map<String, String> msg = new HashMap<>();
            msg.put("role", "user");
            msg.put("content", fullMessage);
            messages.add(msg);
            requestBody.put("messages", messages);

            String responseBody = callApi(objectMapper.writeValueAsString(requestBody), provider, apiKey, baseUrl);
            JsonNode root = objectMapper.readTree(responseBody);

            // Anthropic 格式响应
            if (root.has("content") && root.get("content").isArray() && root.get("content").size() > 0) {
                return root.get("content").get(0).get("text").asText();
            }
            // OpenAI 兼容格式响应
            if (root.has("choices") && root.get("choices").isArray() && root.get("choices").size() > 0) {
                JsonNode choice = root.get("choices").get(0);
                if (choice.has("message") && choice.get("message").has("content")) {
                    return choice.get("message").get("content").asText();
                }
            }
            // 错误处理
            if (root.has("error")) {
                String errorMsg = root.get("error").has("message")
                        ? root.get("error").get("message").asText()
                        : root.get("error").toString();
                log.error("AI API 错误: {}", errorMsg);
                return "AI 请求失败: " + errorMsg;
            }
            return "AI 返回格式异常";
        } catch (Exception e) {
            log.error("AI 助手调用异常", e);
            return "AI 请求失败: " + e.getMessage();
        }
    }

    /**
     * NL2SQL：自然语言转 SQL
     */
    public String nl2sql(String question, String tableSchema) {
        String agentReply = callAssistAgent("nl2sql", question, tableSchema);
        if (agentReply != null && !agentReply.isEmpty()) {
            return agentReply;
        }

        String context = "以下是可用的表结构信息：\n" + tableSchema;
        String prompt = "请根据以下需求生成 SQL 语句：\n" + question + "\n\n要求：只返回可执行的 SQL，用 ```sql 包裹。";
        return chat(prompt, context);
    }

    /**
     * SQL 解释
     */
    public String explainSql(String sql) {
        String agentReply = callAssistAgent("explain", "请解释以下 SQL 的含义：\n```sql\n" + sql + "\n```", "");
        if (agentReply != null && !agentReply.isEmpty()) {
            return agentReply;
        }

        String prompt = "请解释以下 SQL 的含义，包括每个部分的作用：\n```sql\n" + sql + "\n```";
        return chat(prompt, null);
    }

    /**
     * SQL 优化建议
     */
    public String optimizeSql(String sql) {
        String agentReply = callAssistAgent("optimize", "请分析以下 SQL 的性能问题并给出优化建议：\n```sql\n" + sql + "\n```", "");
        if (agentReply != null && !agentReply.isEmpty()) {
            return agentReply;
        }

        String prompt = "请分析以下 SQL 的性能问题并给出优化建议：\n```sql\n" + sql + "\n```";
        return chat(prompt, null);
    }

    /**
     * 新建 SQL 脚本时生成标准模型表名/任务名：优先走 Python Agent 的 LLM + RAG 命名规则。
     */
    public Map<String, Object> generateTableName(GenerateTableNameRequest req) {
        Map<String, Object> result = new HashMap<>();
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("layer", nz(req.getLayer()));
            body.put("tableType", nz(req.getTableType()));
            body.put("subject", nz(req.getSubject()));
            body.put("subSubject", nz(req.getSubSubject()));
            body.put("description", nz(req.getDescription()));
            body.put("dbName", nz(req.getDbName()));
            String response = callAgentEndpoint("/generate-table-name", objectMapper.writeValueAsString(body), 120000);
            if (response != null && !response.isEmpty()) {
                JsonNode root = objectMapper.readTree(response);
                String tableName = text(root, "table_name");
                if (tableName == null || tableName.isEmpty()) tableName = text(root, "tableName");
                if (tableName != null && !tableName.isEmpty()) {
                    tableName = sanitizeTableName(tableName);
                    result.put("tableName", tableName);
                    result.put("reason", text(root, "reason"));
                    result.put("source", "agent-rag");
                    return result;
                }
            }
        } catch (Exception e) {
            log.warn("AI Agent 表名生成失败，降级直连 LLM: {}", e.getMessage());
        }

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
        result.put("tableName", sanitizeTableName(chat(prompt, null)));
        result.put("source", "direct-llm-fallback");
        return result;
    }

    private String callAssistAgent(String mode, String message, String context) {
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("session_id", "datanote-dev-" + (mode == null ? "chat" : mode));
            body.put("mode", mode);
            body.put("message", message);
            body.put("context", context == null ? "" : context);
            String response = callAgentEndpoint("/assist", objectMapper.writeValueAsString(body), 120000);
            if (response == null || response.isEmpty()) return null;
            JsonNode root = objectMapper.readTree(response);
            return text(root, "reply");
        } catch (Exception e) {
            log.warn("AI Agent 调用失败，准备降级直连 LLM: {}", e.getMessage());
            return null;
        }
    }

    private String callAgentEndpoint(String path, String body, int readTimeoutMs) throws Exception {
        String base = agentUrl == null || agentUrl.trim().isEmpty() ? "http://localhost:8000" : agentUrl.trim();
        if (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        URL url = new URL(base + path);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setConnectTimeout(3000);
        conn.setReadTimeout(readTimeoutMs);
        conn.setRequestProperty("Content-Type", "application/json");
        try (OutputStream os = conn.getOutputStream()) {
            os.write(body.getBytes(StandardCharsets.UTF_8));
        }
        int code = conn.getResponseCode();
        java.io.InputStream is = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
        if (is == null) {
            throw new IllegalStateException("Agent 返回空响应，HTTP " + code);
        }
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = is.read(buf)) != -1) {
            baos.write(buf, 0, n);
        }
        is.close();
        String response = new String(baos.toByteArray(), StandardCharsets.UTF_8);
        if (code >= 400) {
            throw new IllegalStateException("Agent HTTP " + code + ": " + response);
        }
        return response;
    }

    private String text(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) return null;
        return node.get(field).asText();
    }

    private String sanitizeTableName(String tableName) {
        if (tableName == null) return "";
        return tableName.trim().toLowerCase().replaceAll("[^a-z0-9_]", "");
    }

    private String nz(String value) {
        return value == null ? "" : value;
    }

    private String callApi(String body, String prov, String key, String base) throws Exception {
        boolean openai = isOpenAiCompatible(prov);
        String endpoint = openai ? "/v1/chat/completions" : "/v1/messages";
        URL url = new URL(base + endpoint);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setConnectTimeout(30000);
        conn.setReadTimeout(120000);
        conn.setRequestProperty("Content-Type", "application/json");
        if (openai) {
            conn.setRequestProperty("Authorization", "Bearer " + key);
        } else {
            conn.setRequestProperty("x-api-key", key);
            conn.setRequestProperty("anthropic-version", "2023-06-01");
        }

        try (OutputStream os = conn.getOutputStream()) {
            os.write(body.getBytes(StandardCharsets.UTF_8));
        }

        int code = conn.getResponseCode();
        java.io.InputStream is = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
        byte[] bytes = new byte[0];
        if (is != null) {
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = is.read(buf)) != -1) {
                baos.write(buf, 0, n);
            }
            bytes = baos.toByteArray();
            is.close();
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    /**
     * 检查 AI 功能是否可用
     */
    public boolean isAvailable() {
        return apiKey != null && !apiKey.isEmpty();
    }

    /**
     * 测试 AI 连接是否正常
     */
    public boolean testConnection(String prov, String testKey, String testBaseUrl, String testModel) {
        if (testKey == null || testKey.isEmpty()) return false;
        try {
            boolean openai = isOpenAiCompatible(prov);
            String base = testBaseUrl != null && !testBaseUrl.isEmpty() ? testBaseUrl : "https://api.anthropic.com";
            String endpoint = openai ? "/v1/chat/completions" : "/v1/messages";
            HttpURLConnection conn = (HttpURLConnection) new URL(base + endpoint).openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            if (openai) {
                conn.setRequestProperty("Authorization", "Bearer " + testKey);
            } else {
                conn.setRequestProperty("x-api-key", testKey);
                conn.setRequestProperty("anthropic-version", "2023-06-01");
            }
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(15000);
            conn.setDoOutput(true);

            String m = testModel != null && !testModel.isEmpty() ? testModel : "claude-sonnet-4-6";
            String body;
            if (openai) {
                body = "{\"model\":\"" + m + "\",\"max_tokens\":10,\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}]}";
            } else {
                body = "{\"model\":\"" + m + "\",\"max_tokens\":10,\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}]}";
            }
            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.getBytes(StandardCharsets.UTF_8));
            }
            int code = conn.getResponseCode();
            return code == 200;
        } catch (Exception e) {
            log.warn("AI connection test failed: {}", e.getMessage());
            return false;
        }
    }
}
