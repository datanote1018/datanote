package com.datanote.service;

import com.datanote.config.HiveConfig;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.datanote.mapper.DnSystemConfigMapper;
import com.datanote.model.DnSystemConfig;
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
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.*;
import java.util.regex.Pattern;

/**
 * AI 辅助开发服务 — 调用 Claude API 实现 NL2SQL、SQL 解释等智能功能
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiAssistService {

    private final ObjectMapper objectMapper;
    private final DnSystemConfigMapper systemConfigMapper;
    private final HiveConfig hiveConfig;

    @Value("${datanote.ai.api-key:}")
    private String envApiKey;

    @Value("${datanote.ai.model:claude-sonnet-4-6}")
    private String envModel;

    @Value("${datanote.ai.base-url:https://api.anthropic.com}")
    private String envBaseUrl;

    @Value("${datanote.crypto.key:}")
    private String cryptoKey;

    // 运行时配置（数据库优先，环境变量兜底）
    private String apiKey;
    private String model;
    private String baseUrl;
    private String provider;  // anthropic / openai / deepseek / bailian / custom

    @PostConstruct
    public void reloadConfig() {
        String dbKey = getDbConfig("ai.api-key");
        if (dbKey != null && !dbKey.isEmpty()) {
            String decrypted = CryptoUtil.decrypt(dbKey, cryptoKey);
            this.apiKey = decrypted != null ? decrypted : dbKey;
        } else {
            this.apiKey = envApiKey;
        }
        String dbModel = getDbConfig("ai.model");
        this.model = (dbModel != null && !dbModel.isEmpty()) ? dbModel : envModel;
        String dbUrl = getDbConfig("ai.base-url");
        this.baseUrl = (dbUrl != null && !dbUrl.isEmpty()) ? dbUrl : envBaseUrl;
        String dbProvider = getDbConfig("ai.provider");
        this.provider = (dbProvider != null && !dbProvider.isEmpty()) ? dbProvider : "anthropic";
        log.info("AI config loaded: provider={}, model={}, baseUrl={}, keyConfigured={}", provider, model, baseUrl, apiKey != null && !apiKey.isEmpty());
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
            "- 回答简洁专业，中文回复";

    private static final int NL2SQL_SCHEMA_TOP_TABLES = 5;
    private static final int NL2SQL_SCHEMA_MAX_COLS = 12;
    private static final int NL2SQL_MAX_REPAIR_ROUNDS = 2;
    private static final int NL2SQL_DEFAULT_LIMIT = 100;
    private static final Set<String> HIVE_SYS_DBS = new HashSet<String>(Arrays.asList(
            "default", "information_schema", "sys"
    ));
    private static final Pattern DANGEROUS_SQL_PATTERN = Pattern.compile(
            "\\b(insert|update|delete|drop|alter|truncate|create|grant|revoke|merge)\\b",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern IDENTIFIER_PATTERN = Pattern.compile("^[a-zA-Z0-9_]+$");

    /**
     * 调用 Claude API 进行对话
     *
     * @param userMessage 用户消息
     * @param context     上下文信息（如表结构、历史对话等）
     * @return AI 回复文本
     */
    public String chat(String userMessage, String context) {
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
        String context = "以下是可用的表结构信息：\n" + tableSchema;
        String prompt = "请根据以下需求生成 SQL 语句：\n" + question + "\n\n要求：只返回可执行的 SQL，用 ```sql 包裹。";
        return chat(prompt, context);
    }

    /**
     * NL2SQL Agent：Schema 召回 + SQL 生成 + 守卫 + Dry-run + 最多2轮修复
     */
    public Map<String, Object> nl2sqlAgent(String question, String tableSchema) {
        Map<String, Object> result = new HashMap<String, Object>();
        List<Map<String, String>> trace = new ArrayList<Map<String, String>>();
        result.put("trace", trace);

        String safeQuestion = question == null ? "" : question.trim();
        if (safeQuestion.isEmpty()) {
            result.put("reply", "问题不能为空");
            result.put("status", "failed");
            result.put("attempts", 0);
            result.put("error", "问题不能为空");
            return result;
        }

        String schemaContext = buildSchemaContext(safeQuestion, tableSchema);
        String prompt = "请根据以下需求生成 Hive SQL。严格要求：\n"
                + "1) 只允许 SELECT 语句\n"
                + "2) 仅返回 ```sql 代码块\n"
                + "3) 不要输出解释文字\n\n"
                + "需求：\n" + safeQuestion;
        String aiReply = chat(prompt, schemaContext);
        String sqlCandidate = extractSqlBlock(aiReply);
        if (sqlCandidate == null || sqlCandidate.trim().isEmpty()) {
            sqlCandidate = aiReply;
        }

        String lastError = null;
        String currentSql = sqlCandidate;
        String lastReply = aiReply;

        for (int round = 0; round <= NL2SQL_MAX_REPAIR_ROUNDS; round++) {
            int attempts = round + 1;
            Map<String, String> traceItem = new LinkedHashMap<String, String>();
            traceItem.put("attempt", String.valueOf(attempts));
            traceItem.put("rawSql", safeSnippet(currentSql));

            String guardedSql;
            try {
                guardedSql = ensureSafeSelectSql(currentSql);
                traceItem.put("guardedSql", safeSnippet(guardedSql));
            } catch (IllegalArgumentException ex) {
                lastError = ex.getMessage();
                traceItem.put("error", lastError);
                trace.add(traceItem);
                result.put("reply", lastReply);
                result.put("status", "failed");
                result.put("attempts", attempts);
                result.put("error", lastError);
                result.put("sql", null);
                return result;
            }

            String dryRunError = dryRunSql(guardedSql);
            if (dryRunError == null) {
                traceItem.put("dryRun", "ok");
                trace.add(traceItem);
                result.put("reply", lastReply);
                result.put("status", "success");
                result.put("attempts", attempts);
                result.put("error", "");
                result.put("sql", guardedSql);
                return result;
            }

            traceItem.put("dryRun", "failed");
            traceItem.put("error", dryRunError);
            trace.add(traceItem);
            lastError = dryRunError;

            if (round >= NL2SQL_MAX_REPAIR_ROUNDS) {
                break;
            }

            String repairPrompt = "下面 SQL 在 Hive 执行失败，请修复为可执行的单条 SELECT 语句。\n"
                    + "严格要求：\n"
                    + "1) 只允许 SELECT\n"
                    + "2) 仅返回 ```sql 代码块\n"
                    + "3) 不要解释\n\n"
                    + "原 SQL：\n```sql\n" + guardedSql + "\n```\n\n"
                    + "错误信息：\n" + dryRunError;
            lastReply = chat(repairPrompt, schemaContext);
            String repaired = extractSqlBlock(lastReply);
            currentSql = (repaired != null && !repaired.trim().isEmpty()) ? repaired : lastReply;
        }

        result.put("reply", lastReply);
        result.put("status", "failed");
        result.put("attempts", NL2SQL_MAX_REPAIR_ROUNDS + 1);
        result.put("error", lastError != null ? lastError : "SQL 修复失败");
        result.put("sql", null);
        return result;
    }

    /**
     * SQL 解释
     */
    public String explainSql(String sql) {
        String prompt = "请解释以下 SQL 的含义，包括每个部分的作用：\n```sql\n" + sql + "\n```";
        return chat(prompt, null);
    }

    /**
     * SQL 优化建议
     */
    public String optimizeSql(String sql) {
        String prompt = "请分析以下 SQL 的性能问题并给出优化建议：\n```sql\n" + sql + "\n```";
        return chat(prompt, null);
    }

    String ensureSafeSelectSql(String sql) {
        String cleaned = stripCodeFence(sql);
        if (cleaned.isEmpty()) {
            throw new IllegalArgumentException("AI 未返回 SQL");
        }
        String compact = cleaned.trim();
        if (compact.endsWith(";")) {
            compact = compact.substring(0, compact.length() - 1).trim();
        }
        if (compact.contains(";")) {
            throw new IllegalArgumentException("仅允许单条 SELECT 语句");
        }
        if (compact.contains("--") || compact.contains("/*")) {
            throw new IllegalArgumentException("SQL 含注释或疑似注入内容，已拒绝执行");
        }
        String lower = compact.toLowerCase(Locale.ROOT);
        if (!lower.startsWith("select")) {
            throw new IllegalArgumentException("仅允许 SELECT 语句");
        }
        if (DANGEROUS_SQL_PATTERN.matcher(lower).find()) {
            throw new IllegalArgumentException("检测到危险关键字，已拒绝执行");
        }
        if (!containsLimit(lower)) {
            compact = compact + " LIMIT " + NL2SQL_DEFAULT_LIMIT;
        }
        return compact;
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

    private String buildSchemaContext(String question, String tableSchema) {
        String trimmedSchema = tableSchema == null ? "" : tableSchema.trim();
        if (!trimmedSchema.isEmpty()) {
            return "以下是可用的表结构信息：\n" + trimmedSchema;
        }
        String recalled = recallSchemaContext(question, NL2SQL_SCHEMA_TOP_TABLES, NL2SQL_SCHEMA_MAX_COLS);
        return recalled.isEmpty() ? "" : ("以下是可用的表结构信息：\n" + recalled);
    }

    private String recallSchemaContext(String question, int topKTables, int maxColumnsPerTable) {
        if (!hiveConfig.isHiveAvailable()) {
            return "";
        }
        List<String> tokens = tokenizeQuestion(question);
        if (tokens.isEmpty()) {
            return "";
        }

        List<TableCandidate> candidates = new ArrayList<TableCandidate>();
        try (Connection conn = hiveConfig.getConnection();
             Statement dbStmt = conn.createStatement();
             ResultSet dbRs = dbStmt.executeQuery("SHOW DATABASES")) {
            while (dbRs.next()) {
                String db = dbRs.getString(1);
                if (db == null || HIVE_SYS_DBS.contains(db.toLowerCase(Locale.ROOT))) {
                    continue;
                }
                if (!isSafeIdentifier(db)) {
                    continue;
                }
                try (Statement tbStmt = conn.createStatement();
                     ResultSet tbRs = tbStmt.executeQuery("SHOW TABLES IN " + db)) {
                    while (tbRs.next()) {
                        String table = tbRs.getString(1);
                        if (table == null || !isSafeIdentifier(table)) {
                            continue;
                        }
                        int score = computeMatchScore(tokens, db, table);
                        if (score > 0) {
                            candidates.add(new TableCandidate(db, table, score));
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Schema recall failed: {}", e.getMessage());
            return "";
        }

        if (candidates.isEmpty()) {
            return "";
        }
        Collections.sort(candidates, new Comparator<TableCandidate>() {
            @Override
            public int compare(TableCandidate a, TableCandidate b) {
                return Integer.compare(b.score, a.score);
            }
        });

        StringBuilder sb = new StringBuilder();
        int n = Math.min(topKTables, candidates.size());
        for (int i = 0; i < n; i++) {
            TableCandidate t = candidates.get(i);
            sb.append(t.db).append(".").append(t.table).append(":\n");
            appendColumns(sb, t.db, t.table, maxColumnsPerTable);
            sb.append("\n");
        }
        return sb.toString().trim();
    }

    private void appendColumns(StringBuilder sb, String db, String table, int maxColumns) {
        if (!isSafeIdentifier(db) || !isSafeIdentifier(table)) {
            sb.append("  - <columns unavailable>\n");
            return;
        }
        try (Connection conn = hiveConfig.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("DESCRIBE " + db + "." + table)) {
            int cnt = 0;
            while (rs.next() && cnt < maxColumns) {
                String col = rs.getString(1);
                if (col == null || col.trim().isEmpty() || col.trim().startsWith("#")) {
                    break;
                }
                String type = rs.getString(2) == null ? "" : rs.getString(2).trim();
                sb.append("  - ").append(col.trim()).append(" ").append(type).append("\n");
                cnt++;
            }
        } catch (Exception e) {
            sb.append("  - <columns unavailable>\n");
        }
    }

    private int computeMatchScore(List<String> tokens, String db, String table) {
        String dbLower = db.toLowerCase(Locale.ROOT);
        String tableLower = table.toLowerCase(Locale.ROOT);
        int score = 0;
        for (String token : tokens) {
            if (dbLower.contains(token)) score += 2;
            if (tableLower.contains(token)) score += 4;
        }
        return score;
    }

    private List<String> tokenizeQuestion(String question) {
        if (question == null) return Collections.emptyList();
        String[] raw = question.toLowerCase(Locale.ROOT).split("[^a-z0-9_\\u4e00-\\u9fa5]+");
        List<String> tokens = new ArrayList<String>();
        for (String t : raw) {
            if (t != null && t.length() >= 2) {
                tokens.add(t);
            }
        }
        return tokens;
    }

    private String dryRunSql(String sql) {
        if (!hiveConfig.isHiveAvailable()) {
            return null;
        }
        try (Connection conn = hiveConfig.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs != null) {
                rs.next();
            }
            return null;
        } catch (Exception e) {
            return e.getMessage() != null ? e.getMessage() : "Dry-run 失败";
        }
    }

    private boolean containsLimit(String lowerSql) {
        return lowerSql.matches("(?s).*\\blimit\\s+\\d+\\b.*");
    }

    private String stripCodeFence(String text) {
        if (text == null) {
            return "";
        }
        String trimmed = text.trim();
        String block = extractSqlBlock(trimmed);
        if (block != null) {
            return block.trim();
        }
        return trimmed.replace("```sql", "")
                .replace("```SQL", "")
                .replace("```", "")
                .trim();
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

    private String safeSnippet(String text) {
        if (text == null) return "";
        String s = text.replace("\n", " ").trim();
        return s.length() > 300 ? s.substring(0, 300) + "..." : s;
    }

    private boolean isSafeIdentifier(String name) {
        return name != null && IDENTIFIER_PATTERN.matcher(name).matches();
    }

    private static final class TableCandidate {
        private final String db;
        private final String table;
        private final int score;

        private TableCandidate(String db, String table, int score) {
            this.db = db;
            this.table = table;
            this.score = score;
        }
    }
}
