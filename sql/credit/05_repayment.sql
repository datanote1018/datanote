-- ============================================================
-- 信贷业务源库 05：还款中心（还款流水 / 逾期 / 提前结清 / 催收）
-- 库：repay_center
-- ============================================================
CREATE DATABASE IF NOT EXISTS repay_center DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE repay_center;

-- 还款交易流水（一次扣款/还款一条，含还本/还息/还罚息拆分）
CREATE TABLE IF NOT EXISTS repay_trans (
  trans_no      VARCHAR(32)   NOT NULL COMMENT '还款流水号',
  loan_no       VARCHAR(32)   NOT NULL COMMENT '借据号',
  cust_no       VARCHAR(32)   NOT NULL COMMENT '客户号',
  period_no     INT           DEFAULT NULL COMMENT '归属期次(可空，多期合并时为空)',
  repay_way     VARCHAR(16)   NOT NULL COMMENT '还款方式 active主动/withhold代扣/offline线下',
  repay_amount  DECIMAL(16,2) NOT NULL COMMENT '还款总金额',
  repay_principal DECIMAL(16,2) NOT NULL DEFAULT 0 COMMENT '其中还本金',
  repay_interest DECIMAL(16,2) NOT NULL DEFAULT 0 COMMENT '其中还利息',
  repay_penalty DECIMAL(16,2) NOT NULL DEFAULT 0 COMMENT '其中还罚息',
  repay_status  VARCHAR(16)   NOT NULL DEFAULT 'success' COMMENT '状态 success成功/fail失败/processing处理中',
  repay_time    DATETIME      NOT NULL COMMENT '还款时间',
  PRIMARY KEY (trans_no),
  KEY idx_loan (loan_no),
  KEY idx_cust (cust_no),
  KEY idx_repay_time (repay_time),
  KEY idx_status (repay_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='还款交易流水';

-- 逾期记录（按借据+期次记录逾期，贷后核心，算逾期率/不良率用）
CREATE TABLE IF NOT EXISTS overdue_record (
  id            BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
  loan_no       VARCHAR(32)   NOT NULL COMMENT '借据号',
  cust_no       VARCHAR(32)   NOT NULL COMMENT '客户号',
  period_no     INT           NOT NULL COMMENT '逾期期次',
  overdue_start_date DATE     NOT NULL COMMENT '逾期开始日期',
  overdue_days  INT           NOT NULL DEFAULT 0 COMMENT '当前逾期天数',
  max_overdue_days INT        NOT NULL DEFAULT 0 COMMENT '历史最大逾期天数',
  overdue_principal DECIMAL(16,2) NOT NULL DEFAULT 0 COMMENT '逾期本金',
  overdue_interest DECIMAL(16,2) NOT NULL DEFAULT 0 COMMENT '逾期利息',
  penalty_amount DECIMAL(16,2) NOT NULL DEFAULT 0 COMMENT '累计罚息',
  overdue_status VARCHAR(16)  NOT NULL DEFAULT 'overdue' COMMENT '状态 overdue逾期中/cured已还清/baddebt核销',
  dpd_level     VARCHAR(16)   DEFAULT NULL COMMENT '逾期分层 M0/M1(1-30)/M2(31-60)/M3(61-90)/M3+',
  cure_date     DATE          DEFAULT NULL COMMENT '结清逾期日期',
  create_time   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_loan_period (loan_no, period_no),
  KEY idx_cust (cust_no),
  KEY idx_status (overdue_status),
  KEY idx_dpd (dpd_level)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='逾期记录';

-- 提前结清（一次性还清剩余本息）
CREATE TABLE IF NOT EXISTS early_settle (
  id            BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
  loan_no       VARCHAR(32)   NOT NULL COMMENT '借据号',
  cust_no       VARCHAR(32)   NOT NULL COMMENT '客户号',
  settle_principal DECIMAL(16,2) NOT NULL COMMENT '结清本金',
  settle_interest DECIMAL(16,2) NOT NULL COMMENT '结清利息',
  break_fee     DECIMAL(16,2) NOT NULL DEFAULT 0 COMMENT '违约金/手续费',
  settle_amount DECIMAL(16,2) NOT NULL COMMENT '结清总金额',
  apply_time    DATETIME      NOT NULL COMMENT '申请时间',
  settle_time   DATETIME      DEFAULT NULL COMMENT '结清完成时间',
  PRIMARY KEY (id),
  KEY idx_loan (loan_no),
  KEY idx_cust (cust_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='提前结清';

-- 催收记录（逾期后的触达，含委外）
CREATE TABLE IF NOT EXISTS collection_record (
  id            BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
  loan_no       VARCHAR(32)   NOT NULL COMMENT '借据号',
  cust_no       VARCHAR(32)   NOT NULL COMMENT '客户号',
  batch_no      VARCHAR(32)   DEFAULT NULL COMMENT '催收批次号',
  collect_way   VARCHAR(16)   NOT NULL COMMENT '催收方式 sms短信/ai智能语音/manual人工/outsource委外',
  collector     VARCHAR(32)   DEFAULT NULL COMMENT '催收员',
  collect_time  DATETIME      NOT NULL COMMENT '催收时间',
  promise_date  DATE          DEFAULT NULL COMMENT '承诺还款日',
  collect_result VARCHAR(16)  DEFAULT NULL COMMENT '结果 promise承诺还款/refuse拒还/uncontact失联/repaid已还',
  remark        VARCHAR(256)  DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id),
  KEY idx_loan (loan_no),
  KEY idx_cust (cust_no),
  KEY idx_collect_time (collect_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='催收记录';
