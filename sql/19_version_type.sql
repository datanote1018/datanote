-- 版本历史增加版本类型字段，区分保存版本和上线版本
ALTER TABLE dn_script_version ADD COLUMN version_type VARCHAR(16) DEFAULT 'save' COMMENT '版本类型(save/online)';

-- 将历史上线版本（commitMsg包含"上线"）标记为 online
UPDATE dn_script_version SET version_type = 'online' WHERE commit_msg LIKE '%上线%';
