package com.datanote.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 认证配置属性
 */
@Data
@Component
@ConfigurationProperties(prefix = "datanote.auth")
public class AuthProperties {

    /** 登录用户名 */
    private String username = "admin";

    /** 登录密码，为空则不启用认证 */
    private String password = "";

    /**
     * 判断是否启用认证（密码非空时启用）
     */
    public boolean isEnabled() {
        return password != null && !password.trim().isEmpty();
    }
}
