-- ============================================================
-- 信贷业务源库 01：基础数据 / 产品 / 资金方 / 渠道
-- 库：base_data
-- ============================================================
CREATE DATABASE IF NOT EXISTS base_data DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE base_data;

-- 数据字典（性别、证件类型、学历、还款方式、各类状态码等统一在此维护）
CREATE TABLE IF NOT EXISTS base_dict (
  id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  dict_type    VARCHAR(64)  NOT NULL COMMENT '字典类型，如 gender/edu/repay_type/loan_status',
  dict_code    VARCHAR(64)  NOT NULL COMMENT '字典编码',
  dict_name    VARCHAR(128) NOT NULL COMMENT '字典名称',
  sort_no      INT          NOT NULL DEFAULT 0 COMMENT '排序',
  status       TINYINT      NOT NULL DEFAULT 1 COMMENT '状态 1启用 0停用',
  create_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_type_code (dict_type, dict_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据字典';

-- 行政区划（省/市/区三级，用于户籍地、居住地维度）
CREATE TABLE IF NOT EXISTS base_region (
  region_code  VARCHAR(12)  NOT NULL COMMENT '行政区划编码',
  region_name  VARCHAR(64)  NOT NULL COMMENT '名称',
  parent_code  VARCHAR(12)  DEFAULT NULL COMMENT '上级编码',
  region_level TINYINT      NOT NULL COMMENT '层级 1省 2市 3区县',
  PRIMARY KEY (region_code),
  KEY idx_parent (parent_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='行政区划';

-- 信贷产品（现金分期 / 循环额度 / 随借随还等）
CREATE TABLE IF NOT EXISTS credit_product (
  product_no     VARCHAR(32)   NOT NULL COMMENT '产品编码',
  product_name   VARCHAR(128)  NOT NULL COMMENT '产品名称',
  product_type   VARCHAR(32)   NOT NULL COMMENT '产品类型 cash现金贷/installment分期/revolving循环额度',
  min_amount     DECIMAL(16,2) NOT NULL COMMENT '最低额度',
  max_amount     DECIMAL(16,2) NOT NULL COMMENT '最高额度',
  year_rate      DECIMAL(8,6)  NOT NULL COMMENT '年化利率(APR) 如0.240000',
  term_options   VARCHAR(64)   NOT NULL COMMENT '可选期数，逗号分隔 如3,6,12,24',
  repay_type     VARCHAR(32)   NOT NULL COMMENT '还款方式 equal等额本息/equal_principal等额本金/interest_first先息后本',
  fund_partner_no VARCHAR(32)  DEFAULT NULL COMMENT '默认资金方编码',
  status         TINYINT       NOT NULL DEFAULT 1 COMMENT '状态 1在售 0下架',
  online_time    DATETIME      DEFAULT NULL COMMENT '上线时间',
  create_time    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (product_no),
  KEY idx_type (product_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='信贷产品';

-- 资金方 / 合作机构（助贷场景：银行、信托、小贷公司提供放款资金）
CREATE TABLE IF NOT EXISTS fund_partner (
  partner_no    VARCHAR(32)   NOT NULL COMMENT '资金方编码',
  partner_name  VARCHAR(128)  NOT NULL COMMENT '机构名称',
  partner_type  VARCHAR(32)   NOT NULL COMMENT '类型 bank银行/trust信托/micro小贷/consumer消金',
  cost_rate     DECIMAL(8,6)  DEFAULT NULL COMMENT '资金成本年化',
  quota_total   DECIMAL(18,2) DEFAULT NULL COMMENT '合作总授信额度',
  status        TINYINT       NOT NULL DEFAULT 1 COMMENT '状态 1合作中 0已终止',
  create_time   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (partner_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='资金方/合作机构';

-- 获客渠道（App、H5、API助贷、信息流广告等）
CREATE TABLE IF NOT EXISTS channel_info (
  channel_no    VARCHAR(32)   NOT NULL COMMENT '渠道编码',
  channel_name  VARCHAR(128)  NOT NULL COMMENT '渠道名称',
  channel_type  VARCHAR(32)   NOT NULL COMMENT '类型 self自营/api助贷api/ad广告投放',
  settle_type   VARCHAR(32)   DEFAULT NULL COMMENT '结算方式 cpa/cps/cpc',
  status         TINYINT      NOT NULL DEFAULT 1 COMMENT '状态 1有效 0停用',
  create_time   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (channel_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='获客渠道';
