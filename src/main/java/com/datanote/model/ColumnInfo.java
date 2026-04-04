package com.datanote.model;

import lombok.Data;

/**
 * 数据库字段信息 — 用于元数据查询和 DDL 生成
 */
@Data
public class ColumnInfo {
    private String name;
    private String type;
    private String comment;
    private String key;       // PRI / MUL / UNI / 空
    private String nullable;  // YES / NO
    private String extra;     // auto_increment 等

    // Hive 映射类型（统一 string）
    private String hiveType = "string";
}
