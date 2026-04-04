package com.datanote.model.dto;

import lombok.Data;

/**
 * DataX 一键建表并同步请求
 */
@Data
public class DataxCreateAndSyncRequest {
    private String db;
    private String table;
    private String syncMode;
    private String datasourceId;
    private Long syncTaskId;
}
