-- DataNote × DolphinScheduler 集成：脚本表增加调度关联字段
ALTER TABLE dn_script
    ADD COLUMN ds_project_code   BIGINT       DEFAULT NULL COMMENT 'DS 项目 code',
    ADD COLUMN ds_workflow_code  BIGINT       DEFAULT NULL COMMENT 'DS 工作流 code',
    ADD COLUMN ds_task_code      BIGINT       DEFAULT NULL COMMENT 'DS 任务 code',
    ADD COLUMN ds_schedule_id    INT          DEFAULT NULL COMMENT 'DS 调度 ID',
    ADD COLUMN schedule_cron     VARCHAR(100) DEFAULT NULL COMMENT 'cron 表达式',
    ADD COLUMN schedule_status   VARCHAR(20)  DEFAULT 'offline' COMMENT '调度状态: offline/online',
    ADD COLUMN timeout_seconds   INT          DEFAULT 0    COMMENT '超时秒数',
    ADD COLUMN retry_times       INT          DEFAULT 0    COMMENT '重试次数',
    ADD COLUMN retry_interval    INT          DEFAULT 60   COMMENT '重试间隔(秒)',
    ADD COLUMN warning_type      VARCHAR(20)  DEFAULT 'NONE' COMMENT '告警类型: NONE/FAILURE/SUCCESS/ALL';
