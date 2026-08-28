-- ============================================================
-- 信贷业务源库 04：贷款中心（用信申请 / 借据放款 / 合同 / 还款计划）
-- 库：loan_center
-- ============================================================
CREATE DATABASE IF NOT EXISTS loan_center DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE loan_center;

-- 借款用信申请（在已有授信额度内发起一笔借款）
CREATE TABLE IF NOT EXISTS loan_apply (
  loan_apply_no VARCHAR(32)   NOT NULL COMMENT '借款申请号',
  account_no    VARCHAR(32)   NOT NULL COMMENT '授信额度账户号',
  cust_no       VARCHAR(32)   NOT NULL COMMENT '客户号',
  product_no    VARCHAR(32)   NOT NULL COMMENT '产品编码',
  apply_amount  DECIMAL(16,2) NOT NULL COMMENT '借款金额',
  term          INT           NOT NULL COMMENT '借款期数(月)',
  loan_purpose  VARCHAR(32)   DEFAULT NULL COMMENT '借款用途 consume消费/education教育/decorate装修/medical医疗',
  apply_status  VARCHAR(16)   NOT NULL DEFAULT 'submit' COMMENT '状态 submit提交/auditing审核/pass通过/reject拒绝/cancel取消',
  apply_time    DATETIME      NOT NULL COMMENT '申请时间',
  PRIMARY KEY (loan_apply_no),
  KEY idx_account (account_no),
  KEY idx_cust (cust_no),
  KEY idx_apply_time (apply_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='借款用信申请';

-- 借据/放款主表（一笔借款一张借据，贷后管理的核心实体）
CREATE TABLE IF NOT EXISTS loan (
  loan_no       VARCHAR(32)   NOT NULL COMMENT '借据号',
  loan_apply_no VARCHAR(32)   NOT NULL COMMENT '借款申请号',
  account_no    VARCHAR(32)   NOT NULL COMMENT '授信额度账户号',
  cust_no       VARCHAR(32)   NOT NULL COMMENT '客户号',
  product_no    VARCHAR(32)   NOT NULL COMMENT '产品编码',
  partner_no    VARCHAR(32)   DEFAULT NULL COMMENT '资金方编码',
  channel_no    VARCHAR(32)   DEFAULT NULL COMMENT '渠道编码',
  loan_amount   DECIMAL(16,2) NOT NULL COMMENT '放款本金',
  year_rate     DECIMAL(8,6)  NOT NULL COMMENT '执行年化利率',
  term          INT           NOT NULL COMMENT '总期数',
  repay_type    VARCHAR(32)   NOT NULL COMMENT '还款方式 equal等额本息/equal_principal等额本金/interest_first先息后本',
  loan_date     DATE          NOT NULL COMMENT '放款日期',
  maturity_date DATE          NOT NULL COMMENT '到期日期',
  loan_status   VARCHAR(16)   NOT NULL DEFAULT 'normal' COMMENT '借据状态 normal正常/settled结清/overdue逾期/baddebt核销',
  remain_principal DECIMAL(16,2) NOT NULL COMMENT '剩余待还本金',
  settle_date   DATE          DEFAULT NULL COMMENT '结清日期',
  create_time   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (loan_no),
  KEY idx_cust (cust_no),
  KEY idx_product (product_no),
  KEY idx_partner (partner_no),
  KEY idx_loan_date (loan_date),
  KEY idx_status (loan_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='借据/放款主表';

-- 借款合同（与借据一对一，电子签）
CREATE TABLE IF NOT EXISTS loan_contract (
  contract_no   VARCHAR(32)   NOT NULL COMMENT '合同号',
  loan_no       VARCHAR(32)   NOT NULL COMMENT '借据号',
  cust_no       VARCHAR(32)   NOT NULL COMMENT '客户号',
  contract_amount DECIMAL(16,2) NOT NULL COMMENT '合同金额',
  sign_time     DATETIME      NOT NULL COMMENT '签署时间',
  contract_url  VARCHAR(256)  DEFAULT NULL COMMENT '合同存储地址',
  PRIMARY KEY (contract_no),
  UNIQUE KEY uk_loan (loan_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='借款合同';

-- 还款计划（按期次拆分，贷后还款/逾期判断的依据）
CREATE TABLE IF NOT EXISTS repay_plan (
  id            BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
  loan_no       VARCHAR(32)   NOT NULL COMMENT '借据号',
  cust_no       VARCHAR(32)   NOT NULL COMMENT '客户号',
  period_no     INT           NOT NULL COMMENT '期次 从1开始',
  due_date      DATE          NOT NULL COMMENT '应还日期',
  due_principal DECIMAL(16,2) NOT NULL COMMENT '应还本金',
  due_interest  DECIMAL(16,2) NOT NULL COMMENT '应还利息',
  due_fee       DECIMAL(16,2) NOT NULL DEFAULT 0 COMMENT '应还费用',
  due_total     DECIMAL(16,2) NOT NULL COMMENT '应还总额',
  paid_amount   DECIMAL(16,2) NOT NULL DEFAULT 0 COMMENT '已还金额',
  plan_status   VARCHAR(16)   NOT NULL DEFAULT 'unpaid' COMMENT '本期状态 unpaid未还/paid已还/overdue逾期/part部分还款',
  actual_pay_date DATE        DEFAULT NULL COMMENT '实际结清本期日期',
  PRIMARY KEY (id),
  UNIQUE KEY uk_loan_period (loan_no, period_no),
  KEY idx_cust (cust_no),
  KEY idx_due_date (due_date),
  KEY idx_status (plan_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='还款计划';
