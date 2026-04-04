-- 告警配置表
CREATE TABLE IF NOT EXISTS dn_alert_config (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  script_id BIGINT NOT NULL COMMENT '关联脚本ID',
  alert_types VARCHAR(256) DEFAULT '["failure"]' COMMENT '告警类型JSON数组(failure/delay/quality)',
  delay_threshold_min INT DEFAULT NULL COMMENT '延迟告警阈值(分钟)',
  quality_rule_ids VARCHAR(512) DEFAULT NULL COMMENT '关联质量规则ID,逗号分隔',
  alert_scope VARCHAR(16) DEFAULT 'personal' COMMENT '告警范围(personal/group)',
  group_id BIGINT DEFAULT NULL COMMENT '告警分组ID',
  enabled TINYINT DEFAULT 1 COMMENT '是否启用',
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_script (script_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='告警配置';
