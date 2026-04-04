-- dn_script 模型增强：任务类型、模型描述、主题域
ALTER TABLE dn_script ADD COLUMN task_type VARCHAR(32) DEFAULT NULL COMMENT '任务类型(核心模型/核心扩展/看板模型/分析模型/重要应用/其他模型)';
ALTER TABLE dn_script ADD COLUMN model_desc TEXT COMMENT '模型描述';
ALTER TABLE dn_script ADD COLUMN subject VARCHAR(64) DEFAULT NULL COMMENT '主题域';
ALTER TABLE dn_script ADD COLUMN sub_subject VARCHAR(64) DEFAULT NULL COMMENT '二级主题';
