package com.datanote.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.datanote.config.HiveConfig;
import com.datanote.mapper.DnDataProfileMapper;
import com.datanote.model.ColumnInfo;
import com.datanote.model.DnDataProfile;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class DataProfileService {

    private final HiveConfig hiveConfig;
    private final DataMapService dataMapService;
    private final AiAssistService aiAssistService;
    private final DnDataProfileMapper profileMapper;
    private final ObjectMapper objectMapper;

    /** 返回缓存的探查报告，不存在则返回 null */
    public DnDataProfile getCached(String db, String table) {
        return profileMapper.selectOne(
                new QueryWrapper<DnDataProfile>().eq("db_name", db).eq("table_name", table));
    }

    /** 运行完整探查（收集统计 → 调 LLM → 存库） */
    public DnDataProfile runProfile(String db, String table) throws Exception {
        Map<String, Object> rawStats = collectStats(db, table);
        String rawJson = objectMapper.writeValueAsString(rawStats);

        String aiReport = callAi(db, table, rawStats);

        DnDataProfile existing = getCached(db, table);
        DnDataProfile record = existing != null ? existing : new DnDataProfile();
        record.setDbName(db);
        record.setTableName(table);
        record.setRawStats(rawJson);
        record.setAiReport(aiReport);
        record.setProfileTime(LocalDateTime.now());

        if (existing != null) {
            profileMapper.updateById(record);
        } else {
            profileMapper.insert(record);
        }
        return record;
    }

    // ── 数据收集 ──────────────────────────────────────────────────────────────

    private Map<String, Object> collectStats(String db, String table) throws Exception {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("db", db);
        result.put("table", table);

        List<ColumnInfo> columns = dataMapService.getHiveColumns(db, table);
        result.put("columnCount", columns.size());

        // 分区信息
        List<String> partitions = new ArrayList<>();
        try (Connection conn = hiveConfig.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SHOW PARTITIONS " + db + "." + table)) {
            while (rs.next()) partitions.add(rs.getString(1));
        } catch (Exception e) {
            log.debug("SHOW PARTITIONS failed: {}", e.getMessage());
        }
        result.put("partitions", partitions);
        String latestPartition = partitions.isEmpty() ? null : partitions.get(partitions.size() - 1);
        result.put("latestPartition", latestPartition);

        // 总行数（限最新分区，无分区就全表）
        String countSql = "SELECT COUNT(*) FROM " + db + "." + table
                + (latestPartition != null ? " WHERE " + latestPartition.replace("=", "='") + "'" : "");
        long totalRows = 0;
        try (Connection conn = hiveConfig.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(countSql)) {
            if (rs.next()) totalRows = rs.getLong(1);
        } catch (Exception e) {
            log.warn("Count query failed: {}", e.getMessage());
        }
        result.put("totalRows", totalRows);

        // 构建字段统计
        List<Map<String, Object>> fieldStats = new ArrayList<>();
        List<String> lowCardFields = new ArrayList<>();   // 低基数字段，待做枚举分布
        List<String> numericFields = new ArrayList<>();   // 数值类字段，待做范围统计

        if (totalRows > 0) {
            // 一次扫描完成 null_count + distinct_count
            StringBuilder selectParts = new StringBuilder("SELECT COUNT(*) AS _total");
            for (ColumnInfo col : columns) {
                String c = "`" + col.getName() + "`";
                selectParts.append(", SUM(CASE WHEN ").append(c).append(" IS NULL THEN 1 ELSE 0 END) AS null_").append(col.getName());
                selectParts.append(", COUNT(DISTINCT ").append(c).append(") AS dist_").append(col.getName());
            }
            String from = " FROM " + db + "." + table
                    + (latestPartition != null ? " WHERE " + latestPartition.replace("=", "='") + "'" : "");
            try (Connection conn = hiveConfig.getConnection();
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(selectParts + from)) {
                if (rs.next()) {
                    for (ColumnInfo col : columns) {
                        Map<String, Object> fs = new LinkedHashMap<>();
                        fs.put("name", col.getName());
                        fs.put("type", col.getType());
                        fs.put("comment", col.getComment());
                        long nc = rs.getLong("null_" + col.getName());
                        long dc = rs.getLong("dist_" + col.getName());
                        fs.put("nullCount", nc);
                        fs.put("nullRate", String.format("%.1f%%", nc * 100.0 / totalRows));
                        fs.put("distinctCount", dc);
                        fieldStats.add(fs);

                        if (dc > 0 && dc <= 25 && nc < totalRows) lowCardFields.add(col.getName());
                        if (col.getType().equalsIgnoreCase("string") && col.getComment() != null
                                && col.getComment().matches(".*[额度金额分数利率期限数量笔数].*")) {
                            numericFields.add(col.getName());
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("Field stats query failed: {}", e.getMessage());
                for (ColumnInfo col : columns) {
                    Map<String, Object> fs = new LinkedHashMap<>();
                    fs.put("name", col.getName());
                    fs.put("type", col.getType());
                    fs.put("comment", col.getComment());
                    fieldStats.add(fs);
                }
            }
        } else {
            for (ColumnInfo col : columns) {
                Map<String, Object> fs = new LinkedHashMap<>();
                fs.put("name", col.getName());
                fs.put("type", col.getType());
                fs.put("comment", col.getComment());
                fieldStats.add(fs);
            }
        }
        result.put("fields", fieldStats);

        // 枚举分布（低基数字段）
        Map<String, List<Map<String, Object>>> enumDists = new LinkedHashMap<>();
        if (totalRows > 0) {
            String partCond = latestPartition != null ? " WHERE " + latestPartition.replace("=", "='") + "'" : "";
            try (Connection conn = hiveConfig.getConnection();
                 Statement stmt = conn.createStatement()) {
                for (String col : lowCardFields) {
                    List<Map<String, Object>> dist = new ArrayList<>();
                    try (ResultSet rs = stmt.executeQuery(
                            "SELECT `" + col + "` AS val, COUNT(*) AS cnt FROM " + db + "." + table
                                    + partCond + " GROUP BY `" + col + "` ORDER BY cnt DESC LIMIT 30")) {
                        while (rs.next()) {
                            Map<String, Object> row = new LinkedHashMap<>();
                            row.put("val", rs.getString("val"));
                            row.put("cnt", rs.getLong("cnt"));
                            row.put("pct", String.format("%.1f%%", rs.getLong("cnt") * 100.0 / totalRows));
                            dist.add(row);
                        }
                    } catch (Exception e) {
                        log.debug("Enum dist failed for {}: {}", col, e.getMessage());
                    }
                    if (!dist.isEmpty()) enumDists.put(col, dist);
                }
            }
        }
        result.put("enumDistributions", enumDists);

        // 数值范围
        Map<String, Map<String, Object>> numericStats = new LinkedHashMap<>();
        if (totalRows > 0 && !numericFields.isEmpty()) {
            StringBuilder numSel = new StringBuilder("SELECT");
            boolean first = true;
            for (String col : numericFields) {
                if (!first) numSel.append(",");
                numSel.append(" MIN(CAST(`").append(col).append("` AS DOUBLE)) AS min_").append(col)
                        .append(", MAX(CAST(`").append(col).append("` AS DOUBLE)) AS max_").append(col)
                        .append(", AVG(CAST(`").append(col).append("` AS DOUBLE)) AS avg_").append(col);
                first = false;
            }
            String partCond = latestPartition != null
                    ? " WHERE " + latestPartition.replace("=", "='") + "'" : "";
            numSel.append(" FROM ").append(db).append(".").append(table).append(partCond);
            try (Connection conn = hiveConfig.getConnection();
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(numSel.toString())) {
                if (rs.next()) {
                    for (String col : numericFields) {
                        Map<String, Object> ns = new LinkedHashMap<>();
                        ns.put("min", round2(rs.getDouble("min_" + col)));
                        ns.put("max", round2(rs.getDouble("max_" + col)));
                        ns.put("avg", round2(rs.getDouble("avg_" + col)));
                        numericStats.put(col, ns);
                    }
                }
            } catch (Exception e) {
                log.debug("Numeric stats failed: {}", e.getMessage());
            }
        }
        result.put("numericStats", numericStats);

        return result;
    }

    // ── LLM 调用 ──────────────────────────────────────────────────────────────

    private String callAi(String db, String table, Map<String, Object> stats) {
        String prompt = buildPrompt(db, table, stats);
        try {
            String reply = aiAssistService.chat(prompt, null);
            // 从回复中提取 JSON（LLM 可能在 JSON 外包文字说明）
            return extractJson(reply);
        } catch (Exception e) {
            log.error("AI profile failed for {}.{}", db, table, e);
            return "{\"summary\":\"AI分析失败: " + e.getMessage() + "\"}";
        }
    }

    @SuppressWarnings("unchecked")
    private String buildPrompt(String db, String table, Map<String, Object> stats) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是一名资深数仓工程师，请对以下 Hive 表做专业数据探查分析，**只输出 JSON，不要任何其他文字**。\n\n");

        sb.append("## 表信息\n");
        sb.append("库名: ").append(db).append("，表名: ").append(table).append("\n");
        sb.append("总行数: ").append(stats.get("totalRows")).append("\n");
        sb.append("字段数: ").append(stats.get("columnCount")).append("\n");
        sb.append("分区列表: ").append(stats.get("partitions")).append("\n\n");

        sb.append("## 字段结构（字段名 | 类型 | 注释 | 空值率 | 去重数）\n");
        List<Map<String, Object>> fields = (List<Map<String, Object>>) stats.get("fields");
        for (Map<String, Object> f : fields) {
            sb.append("- ").append(f.get("name"))
                    .append(" | ").append(f.get("type"))
                    .append(" | ").append(nullStr(f.get("comment")))
                    .append(" | ").append(nullStr(f.get("nullRate")))
                    .append(" | 去重:").append(nullStr(f.get("distinctCount")))
                    .append("\n");
        }

        Map<String, List<Map<String, Object>>> enumDists =
                (Map<String, List<Map<String, Object>>>) stats.get("enumDistributions");
        if (enumDists != null && !enumDists.isEmpty()) {
            sb.append("\n## 枚举字段分布\n");
            for (Map.Entry<String, List<Map<String, Object>>> e : enumDists.entrySet()) {
                sb.append("**").append(e.getKey()).append("**: ");
                for (Map<String, Object> row : e.getValue()) {
                    sb.append(row.get("val")).append("=").append(row.get("cnt"))
                            .append("(").append(row.get("pct")).append(") ");
                }
                sb.append("\n");
            }
        }

        Map<String, Map<String, Object>> numericStats =
                (Map<String, Map<String, Object>>) stats.get("numericStats");
        if (numericStats != null && !numericStats.isEmpty()) {
            sb.append("\n## 数值字段范围\n");
            for (Map.Entry<String, Map<String, Object>> e : numericStats.entrySet()) {
                Map<String, Object> ns = e.getValue();
                sb.append("- ").append(e.getKey())
                        .append(": min=").append(ns.get("min"))
                        .append(" max=").append(ns.get("max"))
                        .append(" avg=").append(ns.get("avg")).append("\n");
            }
        }

        sb.append("\n---\n");
        sb.append("请输出如下 JSON 结构（所有字段必须存在，值为空则用空数组/空字符串）：\n");
        sb.append("{\n");
        sb.append("  \"theme\": \"主题域，如：信贷风控/授信申请\",\n");
        sb.append("  \"businessProcess\": [{\"step\":1,\"name\":\"步骤名\",\"fields\":[\"字段名\"]}],\n");
        sb.append("  \"tableType\": \"累计快照事实表|事务事实表|周期快照事实表|维度表\",\n");
        sb.append("  \"tableTypeReason\": \"一句话说明判断依据\",\n");
        sb.append("  \"isDimension\": false,\n");
        sb.append("  \"nullAnalysis\": [{\"field\":\"字段名\",\"rate\":\"xx%\",\"type\":\"unconditional|conditional|ok\",\"meaning\":\"空值含义说明\"}],\n");
        sb.append("  \"enumConflicts\": [{\"field\":\"字段名\",\"severity\":\"error|warning\",\"desc\":\"矛盾说明\",\"values\":[{\"val\":\"1\",\"commentSays\":\"xxx\",\"dataSays\":\"yyy\"}]}],\n");
        sb.append("  \"crossFieldChecks\": [{\"name\":\"校验名\",\"result\":\"pass|warning|fail\",\"detail\":\"说明\"}],\n");
        sb.append("  \"qualityIssues\": [{\"severity\":\"error|warning|info\",\"field\":\"字段名或空\",\"desc\":\"问题描述\",\"scope\":\"影响范围\"}],\n");
        sb.append("  \"dwdSuggestions\": [{\"action\":\"drop|fixComment|derive|filter|convert\",\"target\":\"字段名或条件\",\"desc\":\"说明\"}],\n");
        sb.append("  \"summary\": \"2-3句话总结该表的业务价值和主要数据质量问题\"\n");
        sb.append("}\n");

        return sb.toString();
    }

    private String extractJson(String reply) {
        if (reply == null) return "{}";
        int start = reply.indexOf('{');
        int end = reply.lastIndexOf('}');
        if (start >= 0 && end > start) {
            String candidate = reply.substring(start, end + 1);
            try {
                objectMapper.readTree(candidate);
                return candidate;
            } catch (Exception e) {
                log.warn("Extracted JSON invalid, returning raw reply");
            }
        }
        return "{\"summary\":" + objectMapper.valueToTree(reply).toString() + "}";
    }

    private String nullStr(Object o) {
        return o == null ? "-" : o.toString();
    }

    private double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
