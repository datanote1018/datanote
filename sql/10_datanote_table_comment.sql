-- 表评论
CREATE TABLE IF NOT EXISTS dn_table_comment (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  table_meta_id BIGINT NOT NULL COMMENT '关联 dn_table_meta.id',
  content       TEXT NOT NULL COMMENT '评论内容',
  created_by    VARCHAR(100) DEFAULT 'default',
  created_at    DATETIME DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_table_meta_id (table_meta_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='表评论';
