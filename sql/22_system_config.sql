-- 系统配置表（AI配置等全局设置）
CREATE TABLE IF NOT EXISTS dn_system_config (
    config_key VARCHAR(100) NOT NULL PRIMARY KEY COMMENT '配置键',
    config_value TEXT COMMENT '配置值（敏感信息加密存储）',
    description VARCHAR(200) COMMENT '配置说明',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统配置表';
