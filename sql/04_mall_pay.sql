-- ============================================
-- 支付中心库 pay_center
-- 负责：支付流水、退款流水
-- ============================================

CREATE DATABASE IF NOT EXISTS pay_center DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_general_ci;
USE pay_center;

-- 支付流水表
CREATE TABLE payment_info (
    id                BIGINT        NOT NULL AUTO_INCREMENT,
    order_id          BIGINT        NOT NULL COMMENT '父订单ID',
    user_id           VARCHAR(32)   NOT NULL COMMENT '用户业务编号',
    pay_type          VARCHAR(20)   NOT NULL COMMENT '支付方式（WECHAT/ALIPAY/BALANCE）',
    out_trade_no      VARCHAR(100)  NOT NULL COMMENT '商户订单号（与第三方对账用）',
    trade_body        VARCHAR(200)  DEFAULT NULL COMMENT '支付主题',
    total_amount      DECIMAL(10,2) NOT NULL COMMENT '支付金额',
    payment_status    VARCHAR(20)   NOT NULL DEFAULT '未支付' COMMENT '状态（未支付/支付中/已支付/支付失败）',
    callback_time     DATETIME      DEFAULT NULL COMMENT '回调时间',
    callback_content  TEXT          DEFAULT NULL COMMENT '回调报文',
    create_time       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_out_trade_no (out_trade_no),
    KEY idx_order_id (order_id),
    KEY idx_user_id (user_id)
) ENGINE=InnoDB COMMENT='支付流水表';

-- 退款流水表
CREATE TABLE refund_payment (
    id                BIGINT        NOT NULL AUTO_INCREMENT,
    order_id          BIGINT        NOT NULL COMMENT '订单ID',
    refund_id         BIGINT        NOT NULL COMMENT '退单申请ID',
    sku_id            BIGINT        DEFAULT NULL COMMENT 'SKU ID',
    pay_type          VARCHAR(20)   NOT NULL COMMENT '原支付方式',
    out_trade_no      VARCHAR(100)  NOT NULL COMMENT '退款流水号',
    refund_amount     DECIMAL(10,2) NOT NULL COMMENT '退款金额',
    refund_status     VARCHAR(20)   NOT NULL DEFAULT '退款中' COMMENT '状态（退款中/已退款/退款失败）',
    callback_time     DATETIME      DEFAULT NULL COMMENT '回调时间',
    callback_content  TEXT          DEFAULT NULL COMMENT '回调报文',
    create_time       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_out_trade_no (out_trade_no),
    KEY idx_order_id (order_id),
    KEY idx_refund_id (refund_id)
) ENGINE=InnoDB COMMENT='退款流水表';
