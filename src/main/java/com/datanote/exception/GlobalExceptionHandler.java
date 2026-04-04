package com.datanote.exception;

import com.datanote.model.R;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器 — 统一异常转换为 R 格式返回
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 资源不存在异常
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public R<?> handleNotFound(ResourceNotFoundException e) {
        log.warn("资源不存在: {}", e.getMessage());
        return R.fail(R.CODE_NOT_FOUND, e.getMessage());
    }

    /**
     * 业务异常：直接返回 message 给前端
     */
    @ExceptionHandler(BusinessException.class)
    public R<?> handleBusiness(BusinessException e) {
        log.warn("业务异常: {}", e.getMessage());
        return R.fail(e.getMessage());
    }

    /**
     * 非预期异常：记录完整堆栈，返回友好提示
     */
    @ExceptionHandler(Exception.class)
    public R<?> handleUnexpected(Exception e) {
        log.error("系统异常", e);
        return R.fail("系统异常: " + e.getMessage());
    }
}
