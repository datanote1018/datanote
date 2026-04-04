package com.datanote.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * 跨域配置 — 允许所有来源的跨域请求
 * <p>
 * 同时暴露 CorsConfigurationSource Bean，供 Spring Security 的 http.cors() 使用，
 * 避免 Security 过滤链与 MVC CORS 配置冲突。
 */
@Configuration
public class CorsConfig {

    /**
     * CORS 配置源 — 同时被 Spring MVC 和 Spring Security 使用
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.addAllowedOriginPattern("*");
        config.addAllowedHeader("*");
        config.addAllowedMethod("*");
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
