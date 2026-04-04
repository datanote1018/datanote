package com.datanote.dto;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 补数据请求 DTO
 */
@Data
public class BackfillRequest {
    private Long taskId;
    private String taskType;
    private LocalDate runDate;
    private LocalDate dateFrom;
    private LocalDate dateTo;
    private String granularity;
    private List<Map<String, Object>> selectedTasks;
}
