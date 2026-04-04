CREATE TABLE IF NOT EXISTS dn_script_version (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    script_id BIGINT NOT NULL COMMENT '脚本ID',
    version INT NOT NULL DEFAULT 1 COMMENT '版本号',
    content LONGTEXT COMMENT '脚本内容快照',
    commit_msg VARCHAR(500) DEFAULT NULL COMMENT '提交说明',
    committed_by VARCHAR(50) DEFAULT NULL COMMENT '提交人',
    committed_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '提交时间',
    PRIMARY KEY (id),
    KEY idx_script_id_committed_at (script_id, committed_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='脚本历史版本表';
