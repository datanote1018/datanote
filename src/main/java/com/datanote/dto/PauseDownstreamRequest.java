package com.datanote.dto;

import lombok.Data;

import java.time.LocalDate;

/**
 * 暂停下游请求 DTO
 */
@Data
public class PauseDownstreamRequest {
    private Long taskId;
    private String taskType;
    private LocalDate runDate;
    private String runType;
}
