-- ============================================================
-- 信贷业务源库 07：资金中心（放款资金 / 还款回流 / 日对账）
-- 库：fund_center
-- ============================================================
CREATE DATABASE IF NOT EXISTS fund_center DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE fund_center;

-- 放款资金流水（资金方实际出资到客户银行卡）
CREATE TABLE IF NOT EXISTS fund_loan_record (
  id            BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
  loan_no       VARCHAR(32)   NOT NULL COMMENT '借据号',
  partner_no    VARCHAR(32)   NOT NULL COMMENT '资金方编码',
  cust_no       VARCHAR(32)   NOT NULL COMMENT '客户号',
  fund_amount   DECIMAL(16,2) NOT NULL COMMENT '出资金额',
  bank_serial   VARCHAR(64)   DEFAULT NULL COMMENT '银行流水号',
  fund_status   VARCHAR(16)   NOT NULL DEFAULT 'success' COMMENT '状态 success成功/fail失败/processing处理中',
  fund_time     DATETIME      NOT NULL COMMENT '放款资金到账时间',
  PRIMARY KEY (id),
  KEY idx_loan (loan_no),
  KEY idx_partner (partner_no),
  KEY idx_fund_time (fund_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='放款资金流水';

-- 还款资金回流（客户还款后按比例回流给资金方）
CREATE TABLE IF NOT EXISTS fund_repay_record (
  id            BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
  trans_no      VARCHAR(32)   NOT NULL COMMENT '还款流水号',
  loan_no       VARCHAR(32)   NOT NULL COMMENT '借据号',
  partner_no    VARCHAR(32)   NOT NULL COMMENT '资金方编码',
  back_amount   DECIMAL(16,2) NOT NULL COMMENT '回流金额',
  back_principal DECIMAL(16,2) NOT NULL DEFAULT 0 COMMENT '其中本金',
  back_interest DECIMAL(16,2) NOT NULL DEFAULT 0 COMMENT '其中利息',
  back_status   VARCHAR(16)   NOT NULL DEFAULT 'success' COMMENT '状态 success成功/fail失败',
  back_time     DATETIME      NOT NULL COMMENT '回流时间',
  PRIMARY KEY (id),
  KEY idx_loan (loan_no),
  KEY idx_partner (partner_no),
  KEY idx_back_time (back_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='还款资金回流';

-- 资金方日对账（每日按资金方汇总放款/还款，核对差异）
CREATE TABLE IF NOT EXISTS reconcile_daily (
  id            BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
  recon_date    DATE          NOT NULL COMMENT '对账日',
  partner_no    VARCHAR(32)   NOT NULL COMMENT '资金方编码',
  loan_count    INT           NOT NULL DEFAULT 0 COMMENT '放款笔数',
  loan_amount   DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '放款金额',
  repay_count   INT           NOT NULL DEFAULT 0 COMMENT '还款笔数',
  repay_amount  DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '还款金额',
  diff_amount   DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '差异金额',
  recon_status  VARCHAR(16)   NOT NULL DEFAULT 'balanced' COMMENT '对账状态 balanced平账/diff有差异',
  create_time   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_date_partner (recon_date, partner_no),
  KEY idx_partner (partner_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='资金方日对账';
