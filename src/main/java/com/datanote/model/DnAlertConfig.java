package com.datanote.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 告警配置实体 — 对应 dn_alert_config 表
 */
@Data
@TableName("dn_alert_config")
public class DnAlertConfig {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long scriptId;
    private String alertTypes;
    private Integer delayThresholdMin;
    private String qualityRuleIds;
    private String alertScope;
    private Long groupId;
    private Integer enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
