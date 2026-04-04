-- DataNote 自研调度系统表

-- 任务依赖关系表（通过解析 SQL 自动计算）
CREATE TABLE IF NOT EXISTS dn_task_dependency (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id       BIGINT       NOT NULL COMMENT '下游任务ID',
    task_type     VARCHAR(16)  NOT NULL COMMENT 'script / syncTask',
    upstream_task_id   BIGINT  NOT NULL COMMENT '上游任务ID',
    upstream_task_type VARCHAR(16) NOT NULL COMMENT 'script / syncTask',
    dep_table     VARCHAR(256) DEFAULT NULL COMMENT '产生依赖的表名',
    UNIQUE KEY uk_dep (task_id, task_type, upstream_task_id, upstream_task_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='任务依赖关系';

-- 每日调度运行记录表
CREATE TABLE IF NOT EXISTS dn_scheduler_run (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id       BIGINT       NOT NULL COMMENT '任务ID',
    task_type     VARCHAR(16)  NOT NULL COMMENT 'script / syncTask',
    run_date      DATE         NOT NULL COMMENT '数据日期（T-1）',
    run_type      VARCHAR(16)  DEFAULT 'daily' COMMENT 'daily=每日调度 / backfill=补数据',
    batch_id      VARCHAR(64)  DEFAULT NULL COMMENT '补数据批次ID',
    status        INT          DEFAULT 0 COMMENT '0=WAITING, 1=SUCCESS, 2=RUNNING, -1=FAILED, -2=PAUSED',
    start_time    DATETIME     DEFAULT NULL,
    end_time      DATETIME     DEFAULT NULL,
    log           LONGTEXT     DEFAULT NULL COMMENT '执行日志',
    retry_count   INT          DEFAULT 0,
    created_at    DATETIME     DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_task_date_type (task_id, task_type, run_date, run_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='调度运行记录';
