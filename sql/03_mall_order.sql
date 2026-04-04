-- ============================================
-- 订单中心库 order_center
-- 负责：购物车、父子订单、订单明细、状态流转、退单
-- ============================================

CREATE DATABASE IF NOT EXISTS order_center DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_general_ci;
USE order_center;

-- 购物车表
CREATE TABLE cart_info (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    user_id         VARCHAR(32)   NOT NULL COMMENT '用户业务编号',
    sku_id          BIGINT        NOT NULL COMMENT 'SKU ID',
    sku_name        VARCHAR(200)  DEFAULT NULL COMMENT '商品名称',
    sku_img         VARCHAR(500)  DEFAULT NULL COMMENT '商品图片',
    sku_price       DECIMAL(10,2) DEFAULT NULL COMMENT '加入时价格（用于比价提示）',
    sku_num         INT           NOT NULL DEFAULT 1 COMMENT '数量',
    is_checked      TINYINT       NOT NULL DEFAULT 1 COMMENT '是否勾选（1是 0否）',
    create_time     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_user_id (user_id)
) ENGINE=InnoDB COMMENT='购物车表';

-- 父订单表
CREATE TABLE order_info (
    id                BIGINT        NOT NULL AUTO_INCREMENT COMMENT '父订单ID',
    order_no          VARCHAR(50)   NOT NULL COMMENT '订单编号',
    user_id           VARCHAR(32)   NOT NULL COMMENT '用户业务编号',
    total_amount      DECIMAL(10,2) NOT NULL COMMENT '商品总金额',
    discount_amount   DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '优惠减免金额',
    freight_amount    DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '运费',
    pay_amount        DECIMAL(10,2) NOT NULL COMMENT '实付金额',
    order_type        VARCHAR(20)   NOT NULL DEFAULT '普通' COMMENT '订单类型（普通/预售/秒杀/拼团）',
    pay_type          VARCHAR(20)   DEFAULT NULL COMMENT '支付方式（WECHAT/ALIPAY/BALANCE）',
    pay_status        VARCHAR(20)   NOT NULL DEFAULT '未支付' COMMENT '支付状态（未支付/已支付）',
    coupon_id         BIGINT        DEFAULT NULL COMMENT '使用的优惠券ID',
    activity_id       BIGINT        DEFAULT NULL COMMENT '参与的活动ID',
    -- 收货地址快照（下单时复制，不跟随用户地址变化）
    receiver_name     VARCHAR(50)   NOT NULL COMMENT '收货人',
    receiver_phone    VARCHAR(20)   NOT NULL COMMENT '收货人电话',
    receiver_province VARCHAR(50)   NOT NULL COMMENT '省',
    receiver_city     VARCHAR(50)   NOT NULL COMMENT '市',
    receiver_district VARCHAR(50)   DEFAULT NULL COMMENT '区',
    receiver_address  VARCHAR(200)  NOT NULL COMMENT '详细地址',
    order_comment     VARCHAR(500)  DEFAULT NULL COMMENT '用户备注',
    order_source      VARCHAR(20)   DEFAULT NULL COMMENT '下单来源（PC/APP/H5/小程序）',
    cancel_reason     VARCHAR(200)  DEFAULT NULL COMMENT '取消原因',
    create_time       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '下单时间',
    pay_time          DATETIME      DEFAULT NULL COMMENT '支付时间',
    cancel_time       DATETIME      DEFAULT NULL COMMENT '取消时间',
    expire_time       DATETIME      DEFAULT NULL COMMENT '未付款自动取消时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_order_no (order_no),
    KEY idx_user_id (user_id),
    KEY idx_create_time (create_time)
) ENGINE=InnoDB COMMENT='父订单表';

-- 子订单表（按仓拆单）
CREATE TABLE order_sub (
    id                  BIGINT        NOT NULL AUTO_INCREMENT COMMENT '子订单ID',
    parent_order_id     BIGINT        NOT NULL COMMENT '父订单ID',
    sub_order_no        VARCHAR(50)   NOT NULL COMMENT '子订单编号',
    warehouse_id        BIGINT        DEFAULT NULL COMMENT '发货仓库ID',
    sub_total_amount    DECIMAL(10,2) NOT NULL COMMENT '子订单商品金额',
    sub_freight_amount  DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '子订单运费',
    sub_status          VARCHAR(20)   NOT NULL DEFAULT '待付款' COMMENT '子订单状态（待付款/已付款/已出库/已发货/已签收/已完成/已取消）',
    deliver_time        DATETIME      DEFAULT NULL COMMENT '发货时间',
    receive_time        DATETIME      DEFAULT NULL COMMENT '签收时间',
    complete_time       DATETIME      DEFAULT NULL COMMENT '完成时间',
    create_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_sub_order_no (sub_order_no),
    KEY idx_parent_order_id (parent_order_id)
) ENGINE=InnoDB COMMENT='子订单表（按仓拆单）';

-- 订单明细表
CREATE TABLE order_detail (
    id                    BIGINT        NOT NULL AUTO_INCREMENT,
    order_id              BIGINT        NOT NULL COMMENT '父订单ID',
    sub_order_id          BIGINT        DEFAULT NULL COMMENT '子订单ID',
    sku_id                BIGINT        NOT NULL COMMENT 'SKU ID',
    -- 商品快照（下单时固化，防止商品修改或下架）
    sku_name              VARCHAR(200)  NOT NULL COMMENT '商品名称（快照）',
    sku_img               VARCHAR(500)  DEFAULT NULL COMMENT '商品图片（快照）',
    sku_attr_value        VARCHAR(500)  DEFAULT NULL COMMENT '规格属性（如：红色/XL）',
    sku_num               INT           NOT NULL COMMENT '购买数量',
    unit_price            DECIMAL(10,2) NOT NULL COMMENT '下单时单价（快照）',
    split_total_amount    DECIMAL(10,2) NOT NULL COMMENT '分摊后小计',
    split_coupon_amount   DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '分摊的优惠券减免',
    split_activity_amount DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '分摊的活动减免',
    create_time           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_order_id (order_id),
    KEY idx_sub_order_id (sub_order_id),
    KEY idx_sku_id (sku_id)
) ENGINE=InnoDB COMMENT='订单明细表';

-- 订单状态流转日志
CREATE TABLE order_status_log (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    order_id        BIGINT       NOT NULL COMMENT '订单ID（父或子）',
    order_type      VARCHAR(10)  NOT NULL DEFAULT 'parent' COMMENT '订单类型（parent/sub）',
    order_status    VARCHAR(20)  NOT NULL COMMENT '操作后状态',
    operator        VARCHAR(50)  DEFAULT NULL COMMENT '操作人（system/客服工号/用户）',
    remark          VARCHAR(200) DEFAULT NULL COMMENT '备注',
    create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_order_id (order_id),
    KEY idx_create_time (create_time)
) ENGINE=InnoDB COMMENT='订单状态流转日志表';

-- 退单申请表
CREATE TABLE order_refund_info (
    id                  BIGINT        NOT NULL AUTO_INCREMENT,
    order_id            BIGINT        NOT NULL COMMENT '父订单ID',
    sub_order_id        BIGINT        DEFAULT NULL COMMENT '子订单ID',
    order_detail_id     BIGINT        NOT NULL COMMENT '订单明细ID（按商品行退）',
    sku_id              BIGINT        NOT NULL COMMENT 'SKU ID',
    user_id             VARCHAR(32)   NOT NULL COMMENT '用户业务编号',
    refund_type         VARCHAR(20)   NOT NULL COMMENT '退款类型（仅退款/退货退款）',
    refund_reason_type  VARCHAR(50)   DEFAULT NULL COMMENT '原因类型（质量问题/发错货/不想要/少件/其他）',
    refund_reason_txt   VARCHAR(500)  DEFAULT NULL COMMENT '原因描述',
    refund_amount       DECIMAL(10,2) NOT NULL COMMENT '退款金额',
    refund_status       VARCHAR(20)   NOT NULL DEFAULT '申请中' COMMENT '状态（申请中/已同意/已拒绝/退货中/已退款/已关闭）',
    create_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '申请时间',
    audit_time          DATETIME      DEFAULT NULL COMMENT '审核时间',
    finish_time         DATETIME      DEFAULT NULL COMMENT '完成时间',
    PRIMARY KEY (id),
    KEY idx_order_id (order_id),
    KEY idx_user_id (user_id)
) ENGINE=InnoDB COMMENT='退单申请表';
