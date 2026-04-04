-- 同步任务增加 DolphinScheduler 调度字段
ALTER TABLE dn_sync_task
  ADD COLUMN ds_project_code  BIGINT       DEFAULT NULL COMMENT 'DS项目code',
  ADD COLUMN ds_workflow_code BIGINT       DEFAULT NULL COMMENT 'DS工作流code',
  ADD COLUMN ds_task_code     BIGINT       DEFAULT NULL COMMENT 'DS任务code',
  ADD COLUMN ds_schedule_id   INT          DEFAULT NULL COMMENT 'DS调度ID',
  ADD COLUMN schedule_cron    VARCHAR(64)  DEFAULT NULL COMMENT 'Cron表达式',
  ADD COLUMN schedule_status  VARCHAR(16)  DEFAULT NULL COMMENT '调度状态: online/offline',
  ADD COLUMN timeout_seconds  INT          DEFAULT 0    COMMENT '超时(秒)',
  ADD COLUMN retry_times      INT          DEFAULT 1    COMMENT '重试次数',
  ADD COLUMN retry_interval   INT          DEFAULT 60   COMMENT '重试间隔(秒)',
  ADD COLUMN warning_type     VARCHAR(16)  DEFAULT 'FAILURE' COMMENT '告警类型';
