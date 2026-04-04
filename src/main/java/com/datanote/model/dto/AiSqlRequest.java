package com.datanote.model.dto;

import lombok.Data;

/**
 * AI SQL 解释/优化请求
 */
@Data
public class AiSqlRequest {
    private String sql;
}
