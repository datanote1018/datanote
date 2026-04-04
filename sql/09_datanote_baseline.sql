-- 基线管理表
CREATE TABLE IF NOT EXISTS dn_baseline (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  baseline_name VARCHAR(200) NOT NULL COMMENT '基线名称',
  description   VARCHAR(500) COMMENT '基线描述',
  commit_time   TIME COMMENT '承诺完成时间',
  priority      INT DEFAULT 1 COMMENT '优先级 1=P1 2=P2 3=P3',
  status        VARCHAR(20) DEFAULT 'enabled' COMMENT 'enabled/disabled',
  created_by    VARCHAR(100) DEFAULT 'default',
  created_at    DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='基线管理';

-- 基线关联任务表
CREATE TABLE IF NOT EXISTS dn_baseline_task (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  baseline_id BIGINT NOT NULL COMMENT '基线ID',
  task_id     BIGINT NOT NULL COMMENT '任务ID',
  task_type   VARCHAR(20) NOT NULL COMMENT 'script/syncTask',
  task_name   VARCHAR(200) COMMENT '任务名称（冗余）',
  created_at  DATETIME DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_baseline_id (baseline_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='基线关联任务';
