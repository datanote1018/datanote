-- 指标定义表
CREATE TABLE IF NOT EXISTS `dn_metric` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `metric_name` VARCHAR(200) NOT NULL COMMENT '指标名称',
  `metric_code` VARCHAR(100) NOT NULL COMMENT '指标编码(唯一)',
  `category` VARCHAR(100) DEFAULT NULL COMMENT '指标分类',
  `description` TEXT COMMENT '指标描述/业务口径',
  `calc_formula` TEXT COMMENT '计算公式/SQL',
  `data_source` VARCHAR(200) DEFAULT NULL COMMENT '数据来源(库.表)',
  `dimensions` VARCHAR(500) DEFAULT NULL COMMENT '统计维度(逗号分隔)',
  `unit` VARCHAR(50) DEFAULT NULL COMMENT '单位(元/次/人等)',
  `owner` VARCHAR(50) DEFAULT NULL COMMENT '负责人',
  `status` INT DEFAULT 1 COMMENT '状态: 1启用 0废弃',
  `tags` VARCHAR(500) DEFAULT NULL COMMENT '标签(逗号分隔)',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_code (`metric_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='指标定义';
