-- 脚本增加库名字段，用于持久化保存脚本所属的 Hive 数据库
ALTER TABLE dn_script ADD COLUMN database_name VARCHAR(100) DEFAULT NULL COMMENT '所属数据库名' AFTER script_type;
