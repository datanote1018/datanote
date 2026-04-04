package com.datanote.model.dto;

import lombok.Data;

/**
 * Cron 表达式预览请求
 */
@Data
public class CronPreviewRequest {
    private String cron;
    private Integer count;  // default 5
}
