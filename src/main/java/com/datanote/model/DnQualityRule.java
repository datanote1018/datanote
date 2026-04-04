package com.datanote.model;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 数据质量规则实体 — 对应 dn_quality_rule 表
 */
@Data
@TableName("dn_quality_rule")
public class DnQualityRule {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String ruleName;
    private String ruleType;
    private Long datasourceId;
    private String databaseName;
    private String tableName;
    private String columnName;
    private String ruleConfig;
    private String customSql;
    private String severity;
    private Integer status;
    private String scheduleCron;
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
