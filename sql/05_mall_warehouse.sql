-- ============================================
-- 仓储物流库 warehouse_center
-- 负责：仓库管理、分仓库存、库存流水、运单、物流轨迹
-- ============================================

CREATE DATABASE IF NOT EXISTS warehouse_center DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_general_ci;
USE warehouse_center;

-- 仓库信息表
CREATE TABLE warehouse_info (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    name            VARCHAR(100) NOT NULL COMMENT '仓库名称（华北仓/华东仓/华南仓）',
    province        VARCHAR(50)  NOT NULL COMMENT '省',
    city            VARCHAR(50)  NOT NULL COMMENT '市',
    address         VARCHAR(200) DEFAULT NULL COMMENT '详细地址',
    type            VARCHAR(20)  NOT NULL DEFAULT '自营仓' COMMENT '类型（自营仓/前置仓/保税仓）',
    contact_phone   VARCHAR(20)  DEFAULT NULL COMMENT '联系电话',
    status          TINYINT      NOT NULL DEFAULT 1 COMMENT '状态（1启用 0停用）',
    create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
) ENGINE=InnoDB COMMENT='仓库信息表';

-- 分仓库存表
CREATE TABLE warehouse_sku_stock (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    warehouse_id    BIGINT       NOT NULL COMMENT '仓库ID',
    sku_id          BIGINT       NOT NULL COMMENT 'SKU ID',
    stock_qty       INT          NOT NULL DEFAULT 0 COMMENT '实际库存',
    locked_qty      INT          NOT NULL DEFAULT 0 COMMENT '锁定库存（已下单未发货）',
    available_qty   INT          NOT NULL DEFAULT 0 COMMENT '可售库存（= stock_qty - locked_qty）',
    update_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_warehouse_sku (warehouse_id, sku_id),
    KEY idx_sku_id (sku_id)
) ENGINE=InnoDB COMMENT='分仓库存表';

-- 库存流水表
CREATE TABLE stock_change_log (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    warehouse_id    BIGINT       NOT NULL COMMENT '仓库ID',
    sku_id          BIGINT       NOT NULL COMMENT 'SKU ID',
    change_type     VARCHAR(20)  NOT NULL COMMENT '变动类型（入库/锁定/扣减/释放/盘点调整）',
    change_qty      INT          NOT NULL COMMENT '变动数量（正数增加，负数减少）',
    before_qty      INT          NOT NULL COMMENT '变动前库存',
    after_qty       INT          NOT NULL COMMENT '变动后库存',
    order_no        VARCHAR(50)  DEFAULT NULL COMMENT '关联订单号',
    operator        VARCHAR(50)  DEFAULT NULL COMMENT '操作人（system/仓管工号）',
    remark          VARCHAR(200) DEFAULT NULL COMMENT '备注',
    create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_warehouse_sku (warehouse_id, sku_id),
    KEY idx_order_no (order_no),
    KEY idx_create_time (create_time)
) ENGINE=InnoDB COMMENT='库存流水表';

-- 物流运单表
CREATE TABLE delivery_info (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    sub_order_id    BIGINT       NOT NULL COMMENT '子订单ID',
    express_company VARCHAR(50)  NOT NULL COMMENT '快递公司（SF/YTO/ZTO/JD/STO）',
    tracking_no     VARCHAR(100) NOT NULL COMMENT '快递单号',
    delivery_status VARCHAR(20)  NOT NULL DEFAULT '已揽收' COMMENT '状态（已揽收/运输中/派送中/已签收/异常）',
    warehouse_id    BIGINT       DEFAULT NULL COMMENT '发货仓库',
    send_time       DATETIME     DEFAULT NULL COMMENT '发货时间',
    receive_time    DATETIME     DEFAULT NULL COMMENT '签收时间',
    PRIMARY KEY (id),
    KEY idx_sub_order_id (sub_order_id),
    KEY idx_tracking_no (tracking_no)
) ENGINE=InnoDB COMMENT='物流运单表';

-- 物流轨迹表
CREATE TABLE delivery_track (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    delivery_id     BIGINT       NOT NULL COMMENT '运单ID',
    track_time      DATETIME     NOT NULL COMMENT '轨迹时间',
    track_status    VARCHAR(20)  NOT NULL COMMENT '状态（揽收/在途/派送/签收/异常）',
    track_info      VARCHAR(500) NOT NULL COMMENT '轨迹描述',
    city            VARCHAR(50)  DEFAULT NULL COMMENT '所在城市',
    PRIMARY KEY (id),
    KEY idx_delivery_id (delivery_id)
) ENGINE=InnoDB COMMENT='物流轨迹表';
