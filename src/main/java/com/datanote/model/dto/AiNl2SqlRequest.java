package com.datanote.model.dto;

import lombok.Data;

/**
 * 自然语言转 SQL 请求
 */
@Data
public class AiNl2SqlRequest {
    private String question;
    private String tableSchema;
}
