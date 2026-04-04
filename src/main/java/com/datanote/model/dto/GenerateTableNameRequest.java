package com.datanote.model.dto;

import lombok.Data;

/**
 * AI 生成标准化表名请求
 */
@Data
public class GenerateTableNameRequest {
    private String layer;       // DWD/DIM/DWS/ADS
    private String tableType;   // 明细表/事务事实表/维度表 etc
    private String subject;     // 主题域
    private String subSubject;  // 二级主题
    private String description; // 模型描述
    private String dbName;      // 数据库名
}
