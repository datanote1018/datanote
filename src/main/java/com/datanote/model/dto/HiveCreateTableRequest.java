package com.datanote.model.dto;

import lombok.Data;

/**
 * Hive 建表请求
 */
@Data
public class HiveCreateTableRequest {
    private String db;
    private String table;
    private String syncMode;
}
