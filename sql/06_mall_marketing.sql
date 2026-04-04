-- ============================================
-- 营销中心库 marketing_center
-- 负责：优惠券、促销活动、活动规则
-- ============================================

CREATE DATABASE IF NOT EXISTS marketing_center DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_general_ci;
USE marketing_center;

-- 优惠券信息表
CREATE TABLE coupon_info (
    id                BIGINT        NOT NULL AUTO_INCREMENT,
    coupon_name       VARCHAR(100)  NOT NULL COMMENT '券名称',
    coupon_type       VARCHAR(20)   NOT NULL COMMENT '类型（满减/折扣/无门槛/运费券）',
    condition_amount  DECIMAL(10,2) DEFAULT NULL COMMENT '使用门槛（满X元可用，无门槛券为NULL）',
    benefit_amount    DECIMAL(10,2) DEFAULT NULL COMMENT '优惠金额（满减券用）',
    benefit_discount  DECIMAL(3,2)  DEFAULT NULL COMMENT '折扣率（折扣券用，如0.85表示85折）',
    scope_type        VARCHAR(20)   NOT NULL DEFAULT '全场' COMMENT '适用范围（全场/品类/品牌/指定SKU）',
    total_count       INT           NOT NULL COMMENT '总发行量',
    taken_count       INT           NOT NULL DEFAULT 0 COMMENT '已领取数',
    used_count        INT           NOT NULL DEFAULT 0 COMMENT '已使用数',
    per_user_limit    INT           NOT NULL DEFAULT 1 COMMENT '每人限领张数',
    start_time        DATETIME      NOT NULL COMMENT '生效时间',
    end_time          DATETIME      NOT NULL COMMENT '失效时间',
    status            VARCHAR(20)   NOT NULL DEFAULT '草稿' COMMENT '状态（草稿/启用/停用/过期）',
    create_time       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_status (status)
) ENGINE=InnoDB COMMENT='优惠券信息表';

-- 领券/用券记录表
CREATE TABLE coupon_use (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    coupon_id       BIGINT       NOT NULL COMMENT '优惠券ID',
    user_id         VARCHAR(32)  NOT NULL COMMENT '用户业务编号',
    order_id        BIGINT       DEFAULT NULL COMMENT '关联订单ID（使用后填入）',
    coupon_status   VARCHAR(20)  NOT NULL DEFAULT '未使用' COMMENT '状态（未使用/已使用/已过期/已退回）',
    get_time        DATETIME     NOT NULL COMMENT '领取时间',
    use_time        DATETIME     DEFAULT NULL COMMENT '使用时间',
    create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_coupon_id (coupon_id),
    KEY idx_user_id (user_id),
    KEY idx_order_id (order_id)
) ENGINE=InnoDB COMMENT='领券/用券记录表';

-- 活动信息表
CREATE TABLE activity_info (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    activity_name   VARCHAR(200) NOT NULL COMMENT '活动名称',
    activity_type   VARCHAR(20)  NOT NULL COMMENT '类型（满减/折扣/秒杀）',
    activity_desc   VARCHAR(500) DEFAULT NULL COMMENT '活动描述',
    start_time      DATETIME     NOT NULL COMMENT '开始时间',
    end_time        DATETIME     NOT NULL COMMENT '结束时间',
    status          VARCHAR(20)  NOT NULL DEFAULT '未开始' COMMENT '状态（未开始/进行中/已结束）',
    create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_status (status)
) ENGINE=InnoDB COMMENT='活动信息表';

-- 活动规则表
CREATE TABLE activity_rule (
    id                BIGINT        NOT NULL AUTO_INCREMENT,
    activity_id       BIGINT        NOT NULL COMMENT '活动ID',
    activity_type     VARCHAR(20)   NOT NULL COMMENT '活动类型',
    condition_amount  DECIMAL(10,2) DEFAULT NULL COMMENT '满足条件金额（满300减50）',
    benefit_amount    DECIMAL(10,2) DEFAULT NULL COMMENT '优惠金额',
    benefit_discount  DECIMAL(3,2)  DEFAULT NULL COMMENT '折扣率',
    benefit_level     INT           DEFAULT NULL COMMENT '优惠级别（阶梯满减时区分档次）',
    PRIMARY KEY (id),
    KEY idx_activity_id (activity_id)
) ENGINE=InnoDB COMMENT='活动规则表';

-- 活动商品关联表
CREATE TABLE activity_sku (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    activity_id     BIGINT        NOT NULL COMMENT '活动ID',
    sku_id          BIGINT        NOT NULL COMMENT 'SKU ID',
    activity_price  DECIMAL(10,2) DEFAULT NULL COMMENT '活动价',
    activity_stock  INT           DEFAULT NULL COMMENT '活动库存（防超卖）',
    PRIMARY KEY (id),
    KEY idx_activity_id (activity_id),
    KEY idx_sku_id (sku_id)
) ENGINE=InnoDB COMMENT='活动商品关联表';
