-- ============================================================
-- 信贷业务源库 06：风控中心（策略规则 / 决策流水 / 评分卡 / 黑名单 / 反欺诈）
-- 库：risk_center
-- ============================================================
CREATE DATABASE IF NOT EXISTS risk_center DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE risk_center;

-- 风控策略/规则（授信、用信各环节的规则配置）
CREATE TABLE IF NOT EXISTS risk_strategy (
  rule_code     VARCHAR(32)   NOT NULL COMMENT '规则编码',
  rule_name     VARCHAR(128)  NOT NULL COMMENT '规则名称',
  stage         VARCHAR(16)   NOT NULL COMMENT '环节 credit授信/loan用信/postloan贷后',
  rule_type     VARCHAR(32)   NOT NULL COMMENT '规则类型 blacklist黑名单/score评分/rule硬规则/antifraud反欺诈',
  threshold_val VARCHAR(64)   DEFAULT NULL COMMENT '阈值/条件表达式',
  action        VARCHAR(16)   NOT NULL COMMENT '命中动作 reject拒绝/review转人工/pass通过/down降额',
  status        TINYINT       NOT NULL DEFAULT 1 COMMENT '状态 1启用 0停用',
  create_time   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (rule_code),
  KEY idx_stage (stage)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='风控策略规则';

-- 风控决策流水（每次审批的命中与决策结果）
CREATE TABLE IF NOT EXISTS risk_decision (
  decision_no   VARCHAR(32)   NOT NULL COMMENT '决策流水号',
  apply_no      VARCHAR(32)   NOT NULL COMMENT '关联申请号(授信或用信)',
  cust_no       VARCHAR(32)   NOT NULL COMMENT '客户号',
  stage         VARCHAR(16)   NOT NULL COMMENT '环节 credit授信/loan用信',
  hit_rules     VARCHAR(256)  DEFAULT NULL COMMENT '命中规则码列表，逗号分隔',
  model_score   INT           DEFAULT NULL COMMENT '模型分',
  decision_result VARCHAR(16) NOT NULL COMMENT '决策结果 pass通过/reject拒绝/review转人工',
  decision_time DATETIME      NOT NULL COMMENT '决策时间',
  PRIMARY KEY (decision_no),
  KEY idx_apply (apply_no),
  KEY idx_cust (cust_no),
  KEY idx_decision_time (decision_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='风控决策流水';

-- 评分卡结果（A卡申请/B卡行为/C卡催收）
CREATE TABLE IF NOT EXISTS risk_score (
  id            BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
  cust_no       VARCHAR(32)   NOT NULL COMMENT '客户号',
  apply_no      VARCHAR(32)   DEFAULT NULL COMMENT '关联申请号',
  card_type     VARCHAR(16)   NOT NULL COMMENT '评分卡类型 A申请/B行为/C催收',
  score         INT           NOT NULL COMMENT '分数',
  score_level   VARCHAR(8)    DEFAULT NULL COMMENT '风险等级 A/B/C/D/E',
  score_time    DATETIME      NOT NULL COMMENT '评分时间',
  PRIMARY KEY (id),
  KEY idx_cust (cust_no),
  KEY idx_card_type (card_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='评分卡结果';

-- 黑名单（证件/手机/设备命中）
CREATE TABLE IF NOT EXISTS blacklist (
  id            BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
  list_type     VARCHAR(16)   NOT NULL COMMENT '名单维度 idcard证件/mobile手机/device设备/bankcard银行卡',
  list_value    VARCHAR(64)   NOT NULL COMMENT '名单值(脱敏)',
  reason        VARCHAR(128)  DEFAULT NULL COMMENT '加入原因 baddebt核销/fraud欺诈/court涉诉',
  source        VARCHAR(32)   DEFAULT NULL COMMENT '来源 internal内部/external外部',
  status        TINYINT       NOT NULL DEFAULT 1 COMMENT '状态 1生效 0解除',
  add_time      DATETIME      NOT NULL COMMENT '加入时间',
  PRIMARY KEY (id),
  KEY idx_value (list_value),
  KEY idx_type (list_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='黑名单';

-- 反欺诈事件（设备聚集、团伙、虚假资料等）
CREATE TABLE IF NOT EXISTS fraud_event (
  id            BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
  cust_no       VARCHAR(32)   NOT NULL COMMENT '客户号',
  event_type    VARCHAR(32)   NOT NULL COMMENT '事件类型 device_gather设备聚集/gang团伙/fake_info虚假资料/multi_apply多头申请',
  hit_rule      VARCHAR(32)   DEFAULT NULL COMMENT '命中规则码',
  risk_level    VARCHAR(8)    NOT NULL COMMENT '风险等级 high/mid/low',
  event_time    DATETIME      NOT NULL COMMENT '发生时间',
  PRIMARY KEY (id),
  KEY idx_cust (cust_no),
  KEY idx_event_type (event_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='反欺诈事件';
