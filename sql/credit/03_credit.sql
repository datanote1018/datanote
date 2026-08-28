-- ============================================================
-- 信贷业务源库 03：授信中心（授信申请 / 审批 / 额度账户 / 征信）
-- 库：credit_center
-- ============================================================
CREATE DATABASE IF NOT EXISTS credit_center DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE credit_center;

-- 授信申请（客户对某产品发起额度申请）
CREATE TABLE IF NOT EXISTS credit_apply (
  apply_no      VARCHAR(32)   NOT NULL COMMENT '授信申请号',
  cust_no       VARCHAR(32)   NOT NULL COMMENT '客户号',
  product_no    VARCHAR(32)   NOT NULL COMMENT '产品编码',
  apply_amount  DECIMAL(16,2) NOT NULL COMMENT '申请额度',
  channel_no    VARCHAR(32)   DEFAULT NULL COMMENT '渠道编码',
  apply_status  VARCHAR(16)   NOT NULL DEFAULT 'submit' COMMENT '申请状态 submit提交/auditing审核中/pass通过/reject拒绝',
  apply_time    DATETIME      NOT NULL COMMENT '申请时间',
  finish_time   DATETIME      DEFAULT NULL COMMENT '审批完成时间',
  PRIMARY KEY (apply_no),
  KEY idx_cust (cust_no),
  KEY idx_product (product_no),
  KEY idx_apply_time (apply_time),
  KEY idx_status (apply_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='授信申请';

-- 授信审批（风控决策结果，审批额度/利率，拒绝码）
CREATE TABLE IF NOT EXISTS credit_audit (
  id            BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
  apply_no      VARCHAR(32)   NOT NULL COMMENT '授信申请号',
  cust_no       VARCHAR(32)   NOT NULL COMMENT '客户号',
  audit_mode    VARCHAR(16)   NOT NULL DEFAULT 'auto' COMMENT '审批方式 auto自动/manual人工',
  audit_result  VARCHAR(16)   NOT NULL COMMENT '审批结果 pass通过/reject拒绝',
  approve_amount DECIMAL(16,2) DEFAULT NULL COMMENT '审批通过额度',
  approve_rate  DECIMAL(8,6)  DEFAULT NULL COMMENT '审批年化利率',
  reject_code   VARCHAR(32)   DEFAULT NULL COMMENT '拒绝码 如 SCORE_LOW/BLACKLIST/OVERDUE_HIST',
  risk_score    INT           DEFAULT NULL COMMENT '综合风险评分',
  auditor       VARCHAR(32)   DEFAULT NULL COMMENT '审批人(人工时)',
  audit_time    DATETIME      NOT NULL COMMENT '审批时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_apply (apply_no),
  KEY idx_cust (cust_no),
  KEY idx_result (audit_result)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='授信审批';

-- 授信额度账户（审批通过后建立循环额度，记录可用/冻结/已用）
CREATE TABLE IF NOT EXISTS credit_account (
  account_no    VARCHAR(32)   NOT NULL COMMENT '额度账户号',
  cust_no       VARCHAR(32)   NOT NULL COMMENT '客户号',
  product_no    VARCHAR(32)   NOT NULL COMMENT '产品编码',
  apply_no      VARCHAR(32)   DEFAULT NULL COMMENT '来源授信申请号',
  credit_amount DECIMAL(16,2) NOT NULL COMMENT '授信总额度',
  used_amount   DECIMAL(16,2) NOT NULL DEFAULT 0 COMMENT '已用额度',
  frozen_amount DECIMAL(16,2) NOT NULL DEFAULT 0 COMMENT '冻结额度',
  avail_amount  DECIMAL(16,2) NOT NULL COMMENT '可用额度',
  exec_rate     DECIMAL(8,6)  NOT NULL COMMENT '执行年化利率',
  account_status VARCHAR(16)  NOT NULL DEFAULT 'normal' COMMENT '账户状态 normal正常/frozen冻结/closed关闭/expired过期',
  effect_date   DATE          NOT NULL COMMENT '额度生效日',
  expire_date   DATE          NOT NULL COMMENT '额度到期日',
  create_time   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (account_no),
  KEY idx_cust (cust_no),
  KEY idx_status (account_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='授信额度账户';

-- 征信查询记录（贷前调取人行/三方征信）
CREATE TABLE IF NOT EXISTS credit_report (
  id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  cust_no       VARCHAR(32)  NOT NULL COMMENT '客户号',
  apply_no      VARCHAR(32)  DEFAULT NULL COMMENT '关联申请号',
  query_org     VARCHAR(32)  NOT NULL COMMENT '查询机构 pboc人行/baihang百行/thirdparty三方',
  query_reason  VARCHAR(32)  NOT NULL COMMENT '查询原因 credit授信审批/loan贷款审批/postloan贷后',
  report_score  INT          DEFAULT NULL COMMENT '征信评分',
  overdue_count INT          DEFAULT NULL COMMENT '历史逾期次数',
  query_count_1m INT         DEFAULT NULL COMMENT '近1月查询次数(多头)',
  query_time    DATETIME     NOT NULL COMMENT '查询时间',
  PRIMARY KEY (id),
  KEY idx_cust (cust_no),
  KEY idx_query_time (query_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='征信查询记录';
