package com.datanote.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import lombok.extern.slf4j.Slf4j;

import javax.annotation.PostConstruct;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Hive 连接配置 — 使用 HikariCP 连接池管理 Hive JDBC 连接
 * 支持 Hive 不可用时优雅降级（应用正常启动，Hive 功能不可用）
 */
@Slf4j
@Configuration
public class HiveConfig {

    @Value("${hive.url:}")
    private String hiveUrl;

    @Value("${hive.username:}")
    private String hiveUsername;

    @Value("${hive.password:}")
    private String hivePassword;

    private HikariDataSource hiveDataSource;
    private boolean hiveAvailable = false;

    @PostConstruct
    public void init() {
        if (hiveUrl == null || hiveUrl.isEmpty()) {
            log.warn("Hive URL 未配置，Hive 功能不可用");
            return;
        }

        try {
            Class.forName("org.apache.hive.jdbc.HiveDriver");
        } catch (ClassNotFoundException e) {
            log.warn("Hive JDBC Driver 未找到，Hive 功能不可用");
            return;
        }

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(hiveUrl);
        config.setUsername(hiveUsername);
        config.setPassword(hivePassword);
        config.setDriverClassName("org.apache.hive.jdbc.HiveDriver");
        config.setMaximumPoolSize(5);
        config.setMinimumIdle(0);
        config.setConnectionTimeout(30000);
        config.setIdleTimeout(600000);
        config.setPoolName("HiveHikariPool");
        config.setConnectionTestQuery("SELECT 1");
        // 关键：允许初始化失败，不阻塞应用启动
        config.setInitializationFailTimeout(-1);

        try {
            hiveDataSource = new HikariDataSource(config);
            hiveAvailable = true;
            log.info("Hive 连接池初始化成功: {}", hiveUrl);
        } catch (Exception e) {
            log.warn("Hive 连接池初始化失败，Hive 功能暂不可用: {}", e.getMessage());
        }
    }

    public boolean isHiveAvailable() {
        return hiveAvailable && hiveDataSource != null;
    }

    /**
     * Hive 数据源 — 不注册为 Spring DataSource Bean，避免覆盖主 MySQL 数据源。
     * 通过 getConnection() 方法获取 Hive 连接即可。
     */

    /**
     * 从连接池获取 Hive JDBC 连接
     *
     * @return Hive 数据库连接
     * @throws SQLException 连接失败时抛出
     */
    public Connection getConnection() throws SQLException {
        if (hiveDataSource == null) {
            throw new SQLException("Hive 未连接。请检查 Hive 服务是否启动。");
        }
        return hiveDataSource.getConnection();
    }

    /**
     * 获取原生 Hive JDBC 连接（绕过 HikariCP 连接池）
     * 用于需要 HiveStatement.getQueryLog() 获取实时执行日志的场景
     */
    public Connection getRawConnection() throws SQLException {
        if (hiveUrl == null || hiveUrl.isEmpty()) {
            throw new SQLException("Hive 未配置。");
        }
        return java.sql.DriverManager.getConnection(hiveUrl, hiveUsername, hivePassword);
    }
}
