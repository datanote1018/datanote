package com.datanote.exception;

/**
 * 资源不存在异常
 */
public class ResourceNotFoundException extends BusinessException {

    public ResourceNotFoundException(String resourceName) {
        super(resourceName + "不存在");
    }

    public ResourceNotFoundException(String resourceName, Long id) {
        super(resourceName + "不存在, id=" + id);
    }
}
