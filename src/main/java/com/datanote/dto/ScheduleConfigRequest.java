package com.datanote.dto;

import lombok.Data;

/**
 * 调度配置请求 DTO
 */
@Data
public class ScheduleConfigRequest {
    private String scheduleCron;
    private Integer timeoutSeconds;
    private Integer retryTimes;
    private Integer retryInterval;
    private String warningType;
}
