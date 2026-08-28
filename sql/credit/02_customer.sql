-- ============================================================
-- 信贷业务源库 02：客户中心（进件主体 / 实名 / 绑卡 / 联系人 / 设备）
-- 库：customer_center
-- ============================================================
CREATE DATABASE IF NOT EXISTS customer_center DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE customer_center;

-- 客户主表（注册即建档，证件/手机做脱敏存储）
CREATE TABLE IF NOT EXISTS customer (
  cust_no       VARCHAR(32)   NOT NULL COMMENT '客户号',
  real_name     VARCHAR(64)   DEFAULT NULL COMMENT '姓名',
  id_card_no    VARCHAR(32)   DEFAULT NULL COMMENT '身份证号(脱敏)',
  mobile        VARCHAR(20)   DEFAULT NULL COMMENT '手机号(脱敏)',
  gender        TINYINT       DEFAULT NULL COMMENT '性别 1男 2女',
  birthday      DATE          DEFAULT NULL COMMENT '出生日期',
  age           INT           DEFAULT NULL COMMENT '年龄',
  education     VARCHAR(16)    DEFAULT NULL COMMENT '学历 highschool/college/bachelor/master',
  marriage      VARCHAR(16)    DEFAULT NULL COMMENT '婚姻 single/married/divorced',
  occupation    VARCHAR(32)    DEFAULT NULL COMMENT '职业类别',
  month_income  DECIMAL(12,2)  DEFAULT NULL COMMENT '月收入',
  census_region VARCHAR(12)    DEFAULT NULL COMMENT '户籍地行政区划编码',
  live_region   VARCHAR(12)    DEFAULT NULL COMMENT '居住地行政区划编码',
  channel_no    VARCHAR(32)    DEFAULT NULL COMMENT '注册渠道编码',
  cust_status   VARCHAR(16)    NOT NULL DEFAULT 'register' COMMENT '客户状态 register注册/authed实名/credited授信/loaned放款/lost流失',
  register_time DATETIME       NOT NULL COMMENT '注册时间',
  create_time   DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time   DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (cust_no),
  KEY idx_mobile (mobile),
  KEY idx_status (cust_status),
  KEY idx_register_time (register_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='客户主表';

-- 实名认证记录（身份证OCR、活体、银行卡四要素等）
CREATE TABLE IF NOT EXISTS customer_auth (
  id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  cust_no      VARCHAR(32)  NOT NULL COMMENT '客户号',
  auth_type    VARCHAR(32)  NOT NULL COMMENT '认证类型 idcard身份证/liveness活体/bankcard4银行卡四要素',
  auth_result  TINYINT      NOT NULL COMMENT '认证结果 1通过 0失败',
  fail_reason  VARCHAR(128) DEFAULT NULL COMMENT '失败原因',
  auth_time    DATETIME     NOT NULL COMMENT '认证时间',
  PRIMARY KEY (id),
  KEY idx_cust (cust_no),
  KEY idx_auth_time (auth_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='实名认证记录';

-- 绑定银行卡（区分代扣卡/收款卡）
CREATE TABLE IF NOT EXISTS customer_bank_card (
  id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  cust_no       VARCHAR(32)  NOT NULL COMMENT '客户号',
  bank_card_no  VARCHAR(32)  NOT NULL COMMENT '银行卡号(脱敏)',
  bank_code     VARCHAR(16)  NOT NULL COMMENT '银行编码',
  bank_name     VARCHAR(64)  NOT NULL COMMENT '银行名称',
  card_type     VARCHAR(16)  NOT NULL DEFAULT 'debit' COMMENT '卡类型 debit借记/credit信用',
  is_withhold   TINYINT      NOT NULL DEFAULT 0 COMMENT '是否代扣卡 1是 0否',
  bind_status   TINYINT      NOT NULL DEFAULT 1 COMMENT '绑定状态 1已绑 0已解绑',
  bind_time     DATETIME     NOT NULL COMMENT '绑定时间',
  PRIMARY KEY (id),
  KEY idx_cust (cust_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='客户绑定银行卡';

-- 紧急联系人（贷前要求填写，催收时使用）
CREATE TABLE IF NOT EXISTS customer_contact (
  id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  cust_no       VARCHAR(32)  NOT NULL COMMENT '客户号',
  relation      VARCHAR(16)  NOT NULL COMMENT '关系 parent父母/spouse配偶/colleague同事/friend朋友',
  contact_name  VARCHAR(64)  DEFAULT NULL COMMENT '联系人姓名',
  contact_mobile VARCHAR(20) DEFAULT NULL COMMENT '联系人手机(脱敏)',
  create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (id),
  KEY idx_cust (cust_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='客户紧急联系人';

-- 设备/登录信息（反欺诈设备指纹、GPS）
CREATE TABLE IF NOT EXISTS customer_device (
  id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  cust_no      VARCHAR(32)  NOT NULL COMMENT '客户号',
  device_id    VARCHAR(64)  NOT NULL COMMENT '设备指纹ID',
  device_model VARCHAR(64)  DEFAULT NULL COMMENT '机型',
  os_type      VARCHAR(16)  DEFAULT NULL COMMENT '系统 ios/android',
  ip           VARCHAR(40)  DEFAULT NULL COMMENT '登录IP',
  gps_region   VARCHAR(12)  DEFAULT NULL COMMENT 'GPS定位行政区划',
  login_time   DATETIME     NOT NULL COMMENT '登录时间',
  PRIMARY KEY (id),
  KEY idx_cust (cust_no),
  KEY idx_device (device_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='客户设备/登录';
