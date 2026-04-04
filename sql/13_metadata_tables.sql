-- 表元数据注释表
CREATE TABLE IF NOT EXISTS `dn_table_meta` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `datasource_id` BIGINT NOT NULL COMMENT '数据源ID',
  `database_name` VARCHAR(100) NOT NULL COMMENT '数据库名',
  `table_name` VARCHAR(200) NOT NULL COMMENT '表名',
  `table_comment` VARCHAR(500) DEFAULT NULL COMMENT '业务描述',
  `owner` VARCHAR(50) DEFAULT NULL COMMENT '负责人',
  `tags` VARCHAR(500) DEFAULT NULL COMMENT '标签(逗号分隔)',
  `importance` VARCHAR(20) DEFAULT 'normal' COMMENT '重要性: core/important/normal',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_table (`datasource_id`, `database_name`, `table_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='表元数据';

-- 字段注释表
CREATE TABLE IF NOT EXISTS `dn_column_meta` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `table_meta_id` BIGINT NOT NULL COMMENT '表元数据ID',
  `column_name` VARCHAR(200) NOT NULL COMMENT '字段名',
  `business_name` VARCHAR(200) DEFAULT NULL COMMENT '业务名称',
  `business_desc` VARCHAR(500) DEFAULT NULL COMMENT '业务描述',
  `tags` VARCHAR(500) DEFAULT NULL COMMENT '标签(逗号分隔)',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_column (`table_meta_id`, `column_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='字段元数据';
