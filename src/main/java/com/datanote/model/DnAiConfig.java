package com.datanote.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 模型配置实体 — 对应 dn_ai_config 表（支持多套配置）
 */
@Data
@TableName("dn_ai_config")
public class DnAiConfig {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 配置名称 */
    private String name;
    /** provider: bailian/anthropic/openai/deepseek/custom */
    private String provider;
    /** API Base URL */
    private String baseUrl;
    /** 模型 */
    private String model;
    /** API Key（AES 加密存储） */
    private String apiKey;
    /** 1=默认（Python 读这套） */
    private Integer isDefault;
    /** 1 启用 0 禁用 */
    private Integer status;
    private LocalDateTime createdAt;
}
