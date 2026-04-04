-- 数据质量规则表
CREATE TABLE IF NOT EXISTS `dn_quality_rule` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `rule_name` VARCHAR(200) NOT NULL COMMENT '规则名称',
  `rule_type` VARCHAR(50) NOT NULL COMMENT '规则类型: null_check/unique_check/value_range/regex_check/custom_sql',
  `datasource_id` BIGINT NOT NULL COMMENT '数据源ID',
  `database_name` VARCHAR(100) NOT NULL COMMENT '数据库名',
  `table_name` VARCHAR(200) NOT NULL COMMENT '表名',
  `column_name` VARCHAR(200) DEFAULT NULL COMMENT '字段名(custom_sql时可空)',
  `rule_config` TEXT COMMENT '规则配置JSON，如 {"min":0,"max":100} 或 {"pattern":"^\\d+$"}',
  `custom_sql` TEXT COMMENT '自定义SQL(rule_type=custom_sql时使用)',
  `severity` VARCHAR(20) DEFAULT 'warning' COMMENT '严重级别: info/warning/error',
  `status` INT DEFAULT 1 COMMENT '状态: 1启用 0禁用',
  `schedule_cron` VARCHAR(100) DEFAULT NULL COMMENT '调度cron表达式',
  `created_by` VARCHAR(50) DEFAULT NULL,
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据质量规则';

-- 数据质量检查执行记录表
CREATE TABLE IF NOT EXISTS `dn_quality_run` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `rule_id` BIGINT NOT NULL COMMENT '规则ID',
  `run_status` VARCHAR(20) NOT NULL COMMENT '运行状态: success/failed/error',
  `total_count` BIGINT DEFAULT 0 COMMENT '总记录数',
  `pass_count` BIGINT DEFAULT 0 COMMENT '通过数',
  `fail_count` BIGINT DEFAULT 0 COMMENT '失败数',
  `pass_rate` DECIMAL(5,2) DEFAULT 0 COMMENT '通过率(%)',
  `error_sample` TEXT COMMENT '异常样本(JSON数组,最多10条)',
  `exec_sql` TEXT COMMENT '实际执行的SQL',
  `duration_ms` BIGINT DEFAULT 0 COMMENT '执行耗时(毫秒)',
  `error_msg` TEXT COMMENT '错误信息',
  `started_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `finished_at` DATETIME DEFAULT NULL,
  INDEX idx_rule_id (`rule_id`),
  INDEX idx_started_at (`started_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据质量检查记录';
