-- 数据地图增强：收藏、搜索记录、浏览量

-- 表收藏
CREATE TABLE IF NOT EXISTS `dn_table_favorite` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `database_name` VARCHAR(100) NOT NULL COMMENT '数据库名',
  `table_name` VARCHAR(200) NOT NULL COMMENT '表名',
  `created_by` VARCHAR(100) DEFAULT 'default',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_fav (`database_name`, `table_name`, `created_by`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='表收藏';

-- 最近搜索记录
CREATE TABLE IF NOT EXISTS `dn_search_history` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `database_name` VARCHAR(100) NOT NULL COMMENT '数据库名',
  `table_name` VARCHAR(200) NOT NULL COMMENT '表名',
  `created_by` VARCHAR(100) DEFAULT 'default',
  `searched_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_search (`database_name`, `table_name`, `created_by`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='搜索历史';

-- 表浏览量（热门排行用）
ALTER TABLE `dn_table_meta` ADD COLUMN IF NOT EXISTS `view_count` INT DEFAULT 0 COMMENT '浏览次数';
ALTER TABLE `dn_table_meta` ADD COLUMN IF NOT EXISTS `row_count` BIGINT DEFAULT NULL COMMENT '表行数估算';
