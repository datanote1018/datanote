package com.datanote.exception;

/**
 * 业务异常 — 可直接将 message 返回给前端
 */
public class BusinessException extends RuntimeException {

    public BusinessException(String message) {
        super(message);
    }

    public BusinessException(String message, Throwable cause) {
        super(message, cause);
    }
}
