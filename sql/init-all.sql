-- ============================================================
-- DataNote 完整初始化脚本 (init-all.sql)
-- 生成时间: 2026-04-03
-- 说明: 包含所有建表语句（无 ALTER TABLE），可直接用于全新部署
-- 顺序: 商城示例库 (01-07) → DataNote 系统表 → 示例数据
-- ============================================================
SET NAMES utf8mb4;
SET CHARACTER SET utf8mb4;
SET character_set_connection = utf8mb4;

-- ============================================
-- 用户中心库 user_center
-- 负责：用户注册、登录、地址、收藏、账户
-- ============================================

CREATE DATABASE IF NOT EXISTS user_center DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_general_ci;
USE user_center;

-- 用户信息表
CREATE TABLE user_info (
    id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    user_id         VARCHAR(32)  NOT NULL COMMENT '用户业务编号（对外使用，如 U100001）',
    login_name      VARCHAR(50)  NOT NULL COMMENT '登录账号',
    nickname        VARCHAR(100) DEFAULT NULL COMMENT '昵称',
    password        VARCHAR(200) NOT NULL COMMENT '密码（加密）',
    phone           VARCHAR(20)  DEFAULT NULL COMMENT '手机号（脱敏存储 138****6789）',
    email           VARCHAR(100) DEFAULT NULL COMMENT '邮箱',
    gender          CHAR(1)      DEFAULT 'U' COMMENT '性别（M男 F女 U未知）',
    birthday        DATE         DEFAULT NULL COMMENT '生日',
    avatar          VARCHAR(500) DEFAULT NULL COMMENT '头像URL',
    user_level      VARCHAR(20)  DEFAULT '普通' COMMENT '会员等级（普通/铜牌/银牌/金牌/钻石）',
    status          TINYINT      NOT NULL DEFAULT 1 COMMENT '状态（1正常 2冻结 3注销）',
    register_channel VARCHAR(20) DEFAULT NULL COMMENT '注册渠道（APP/H5/小程序/PC）',
    register_ip     VARCHAR(50)  DEFAULT NULL COMMENT '注册IP',
    create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '注册时间',
    update_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_id (user_id),
    UNIQUE KEY uk_login_name (login_name),
    KEY idx_phone (phone),
    KEY idx_create_time (create_time)
) ENGINE=InnoDB COMMENT='用户信息表';

-- 用户实名认证表
CREATE TABLE user_real_auth (
    id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    user_id         VARCHAR(32)  NOT NULL COMMENT '用户业务编号',
    real_name       VARCHAR(50)  NOT NULL COMMENT '真实姓名（脱敏存储 张*）',
    id_card_no      VARCHAR(30)  NOT NULL COMMENT '身份证号（脱敏存储 110***********1234）',
    auth_status     VARCHAR(20)  NOT NULL DEFAULT '未认证' COMMENT '认证状态（未认证/已认证/认证失败）',
    auth_time       DATETIME     DEFAULT NULL COMMENT '认证时间',
    create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_id (user_id)
) ENGINE=InnoDB COMMENT='用户实名认证表';

-- 收货地址表
CREATE TABLE user_address (
    id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '地址ID',
    user_id         VARCHAR(32)  NOT NULL COMMENT '用户业务编号',
    receiver_name   VARCHAR(50)  NOT NULL COMMENT '收货人姓名',
    receiver_phone  VARCHAR(20)  NOT NULL COMMENT '收货人电话',
    province        VARCHAR(50)  NOT NULL COMMENT '省',
    city            VARCHAR(50)  NOT NULL COMMENT '市',
    district        VARCHAR(50)  DEFAULT NULL COMMENT '区',
    detail_address  VARCHAR(200) NOT NULL COMMENT '详细地址',
    label           VARCHAR(20)  DEFAULT NULL COMMENT '地址标签（家/公司/学校）',
    is_default      TINYINT      NOT NULL DEFAULT 0 COMMENT '是否默认（1是 0否）',
    create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_user_id (user_id)
) ENGINE=InnoDB COMMENT='收货地址表';

-- 用户收藏表
CREATE TABLE favor_info (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    user_id         VARCHAR(32)  NOT NULL COMMENT '用户业务编号',
    sku_id          BIGINT       NOT NULL COMMENT 'SKU ID',
    create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_sku (user_id, sku_id),
    KEY idx_user_id (user_id)
) ENGINE=InnoDB COMMENT='用户收藏表';

-- 登录日志表
CREATE TABLE user_login_log (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    user_id         VARCHAR(32)  NOT NULL COMMENT '用户业务编号',
    login_time      DATETIME     NOT NULL COMMENT '登录时间',
    login_ip        VARCHAR(50)  DEFAULT NULL COMMENT '登录IP',
    device_type     VARCHAR(20)  DEFAULT NULL COMMENT '设备类型（iOS/Android/PC）',
    login_channel   VARCHAR(20)  DEFAULT NULL COMMENT '登录渠道（APP/H5/小程序）',
    PRIMARY KEY (id),
    KEY idx_user_id (user_id),
    KEY idx_login_time (login_time)
) ENGINE=InnoDB COMMENT='登录日志表';

-- ============================================
-- 商品中心库 product_center
-- 负责：分类、品牌、SPU/SKU、属性体系
-- ============================================

CREATE DATABASE IF NOT EXISTS product_center DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_general_ci;
USE product_center;

-- 一级分类
CREATE TABLE base_category1 (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    name            VARCHAR(50)  NOT NULL COMMENT '分类名称',
    PRIMARY KEY (id)
) ENGINE=InnoDB COMMENT='一级分类表';

-- 二级分类
CREATE TABLE base_category2 (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    name            VARCHAR(50)  NOT NULL COMMENT '分类名称',
    category1_id    BIGINT       NOT NULL COMMENT '一级分类ID',
    PRIMARY KEY (id),
    KEY idx_category1_id (category1_id)
) ENGINE=InnoDB COMMENT='二级分类表';

-- 三级分类
CREATE TABLE base_category3 (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    name            VARCHAR(50)  NOT NULL COMMENT '分类名称',
    category2_id    BIGINT       NOT NULL COMMENT '二级分类ID',
    PRIMARY KEY (id),
    KEY idx_category2_id (category2_id)
) ENGINE=InnoDB COMMENT='三级分类表';

-- 品牌表
CREATE TABLE base_trademark (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    tm_name         VARCHAR(100) NOT NULL COMMENT '品牌名称',
    logo_url        VARCHAR(500) DEFAULT NULL COMMENT '品牌LOGO',
    PRIMARY KEY (id)
) ENGINE=InnoDB COMMENT='品牌表';

-- SPU 标准产品表
CREATE TABLE spu_info (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    spu_name        VARCHAR(200) NOT NULL COMMENT 'SPU名称',
    description     VARCHAR(1000) DEFAULT NULL COMMENT '商品描述',
    category3_id    BIGINT       NOT NULL COMMENT '三级分类ID',
    tm_id           BIGINT       NOT NULL COMMENT '品牌ID',
    create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_category3_id (category3_id),
    KEY idx_tm_id (tm_id)
) ENGINE=InnoDB COMMENT='SPU标准产品表';

-- 商品海报图
CREATE TABLE spu_poster (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    spu_id          BIGINT       NOT NULL COMMENT 'SPU ID',
    img_url         VARCHAR(500) NOT NULL COMMENT '图片URL',
    PRIMARY KEY (id),
    KEY idx_spu_id (spu_id)
) ENGINE=InnoDB COMMENT='商品海报图表';

-- SKU 库存单元表
CREATE TABLE sku_info (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    spu_id          BIGINT       NOT NULL COMMENT '所属SPU',
    sku_name        VARCHAR(200) NOT NULL COMMENT 'SKU名称',
    sku_default_img VARCHAR(500) DEFAULT NULL COMMENT '默认图片',
    price           DECIMAL(10,2) NOT NULL COMMENT '销售价',
    market_price    DECIMAL(10,2) DEFAULT NULL COMMENT '市场价（划线价）',
    cost_price      DECIMAL(10,2) DEFAULT NULL COMMENT '成本价',
    weight          DECIMAL(10,2) DEFAULT NULL COMMENT '重量（kg）',
    barcode         VARCHAR(50)  DEFAULT NULL COMMENT '商品条码',
    sale_count      INT          NOT NULL DEFAULT 0 COMMENT '累计销量',
    status          TINYINT      NOT NULL DEFAULT 1 COMMENT '状态（1上架 0下架）',
    create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_spu_id (spu_id)
) ENGINE=InnoDB COMMENT='SKU库存单元表';

-- 平台属性定义表
CREATE TABLE base_attr_info (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    attr_name       VARCHAR(50)  NOT NULL COMMENT '属性名（CPU型号/屏幕尺寸）',
    category_id     BIGINT       NOT NULL COMMENT '所属分类',
    attr_type       TINYINT      NOT NULL DEFAULT 0 COMMENT '属性类型（0规格参数 1销售属性）',
    PRIMARY KEY (id),
    KEY idx_category_id (category_id)
) ENGINE=InnoDB COMMENT='平台属性定义表';

-- 平台属性值表
CREATE TABLE base_attr_value (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    value_name      VARCHAR(100) NOT NULL COMMENT '属性值',
    attr_id         BIGINT       NOT NULL COMMENT '所属属性ID',
    PRIMARY KEY (id),
    KEY idx_attr_id (attr_id)
) ENGINE=InnoDB COMMENT='平台属性值表';

-- SKU平台属性关联表
CREATE TABLE sku_attr_value (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    sku_id          BIGINT       NOT NULL COMMENT 'SKU ID',
    attr_id         BIGINT       NOT NULL COMMENT '属性ID',
    value_id        BIGINT       NOT NULL COMMENT '属性值ID',
    attr_name       VARCHAR(50)  DEFAULT NULL COMMENT '属性名（冗余）',
    value_name      VARCHAR(100) DEFAULT NULL COMMENT '属性值（冗余）',
    PRIMARY KEY (id),
    KEY idx_sku_id (sku_id)
) ENGINE=InnoDB COMMENT='SKU平台属性关联表';

-- 销售属性定义表
CREATE TABLE base_sale_attr (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    name            VARCHAR(50)  NOT NULL COMMENT '销售属性名（颜色/尺码/版本）',
    PRIMARY KEY (id)
) ENGINE=InnoDB COMMENT='销售属性定义表';

-- SKU销售属性值表
CREATE TABLE sku_sale_attr_value (
    id                    BIGINT       NOT NULL AUTO_INCREMENT,
    sku_id                BIGINT       NOT NULL COMMENT 'SKU ID',
    spu_id                BIGINT       NOT NULL COMMENT 'SPU ID',
    sale_attr_id          BIGINT       NOT NULL COMMENT '销售属性ID',
    sale_attr_name        VARCHAR(50)  DEFAULT NULL COMMENT '销售属性名（颜色）',
    sale_attr_value_name  VARCHAR(100) DEFAULT NULL COMMENT '销售属性值（星空黑）',
    PRIMARY KEY (id),
    KEY idx_sku_id (sku_id),
    KEY idx_spu_id (spu_id)
) ENGINE=InnoDB COMMENT='SKU销售属性值表';

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

-- ============================================
-- 基础数据库 base_data
-- 负责：省份地区、数据字典、评价、SKU成本
-- ============================================

CREATE DATABASE IF NOT EXISTS base_data DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_general_ci;
USE base_data;

-- 省份表
CREATE TABLE base_province (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    name            VARCHAR(50)  NOT NULL COMMENT '省份名称',
    region_id       BIGINT       DEFAULT NULL COMMENT '所属地区ID',
    area_code       VARCHAR(20)  DEFAULT NULL COMMENT '区号',
    iso_code        VARCHAR(20)  DEFAULT NULL COMMENT 'ISO编码',
    PRIMARY KEY (id)
) ENGINE=InnoDB COMMENT='省份表';

-- 地区表
CREATE TABLE base_region (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    region_name     VARCHAR(50)  NOT NULL COMMENT '地区名称（华北/华东/华南/华中/西南/西北/东北）',
    PRIMARY KEY (id)
) ENGINE=InnoDB COMMENT='地区表';

-- 数据字典表
CREATE TABLE base_dic (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    dic_type        VARCHAR(50)  NOT NULL COMMENT '字典类型（order_status/pay_type/refund_status...）',
    dic_code        VARCHAR(50)  NOT NULL COMMENT '字典编码',
    dic_name        VARCHAR(100) NOT NULL COMMENT '字典名称',
    sort_order      INT          DEFAULT 0 COMMENT '排序',
    PRIMARY KEY (id),
    UNIQUE KEY uk_type_code (dic_type, dic_code),
    KEY idx_dic_type (dic_type)
) ENGINE=InnoDB COMMENT='数据字典表';

-- 商品评价表
CREATE TABLE comment_info (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    user_id         VARCHAR(32)  NOT NULL COMMENT '用户业务编号',
    sku_id          BIGINT       NOT NULL COMMENT 'SKU ID',
    spu_id          BIGINT       NOT NULL COMMENT 'SPU ID',
    order_id        BIGINT       NOT NULL COMMENT '订单ID',
    order_detail_id BIGINT       NOT NULL COMMENT '订单明细ID',
    rating          TINYINT      NOT NULL DEFAULT 1 COMMENT '评分（1好评 2中评 3差评）',
    content         TEXT         DEFAULT NULL COMMENT '评价内容',
    img_urls        VARCHAR(2000) DEFAULT NULL COMMENT '评价图片（JSON数组）',
    create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_sku_id (sku_id),
    KEY idx_user_id (user_id),
    KEY idx_order_id (order_id)
) ENGINE=InnoDB COMMENT='商品评价表';

-- SKU成本表（财务域）
CREATE TABLE financial_sku_cost (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    sku_id          BIGINT        NOT NULL COMMENT 'SKU ID',
    cost_price      DECIMAL(10,2) NOT NULL COMMENT '成本价',
    create_time     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_sku_id (sku_id)
) ENGINE=InnoDB COMMENT='SKU成本表（财务域）';


-- ============================================================
-- DataNote 系统表
-- ============================================================
CREATE DATABASE IF NOT EXISTS datanote DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_general_ci;
USE datanote;

-- 数据源配置表
CREATE TABLE IF NOT EXISTS dn_datasource (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    name          VARCHAR(100) NOT NULL COMMENT '数据源名称',
    type          VARCHAR(20)  NOT NULL COMMENT 'MySQL/Hive/PostgreSQL/Oracle',
    host          VARCHAR(200) NOT NULL,
    port          INT          NOT NULL,
    database_name VARCHAR(100) DEFAULT '' COMMENT '数据库名',
    username      VARCHAR(100) DEFAULT '' COMMENT '用户名',
    password      VARCHAR(200) DEFAULT '' COMMENT '加密存储',
    extra_params  VARCHAR(500) DEFAULT '' COMMENT '额外连接参数',
    status        TINYINT      DEFAULT 1 COMMENT '1可用 0不可用',
    created_by    VARCHAR(50)  DEFAULT '',
    created_at    DATETIME     DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据源配置';

-- 脚本目录
CREATE TABLE IF NOT EXISTS dn_script_folder (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    folder_name VARCHAR(100) NOT NULL COMMENT '目录名称',
    parent_id   BIGINT       DEFAULT 0 COMMENT '父目录ID，0为根目录',
    layer       VARCHAR(20)  DEFAULT '' COMMENT '数仓层级：ODS/DWD/DWS/ADS/脚本/数据集成',
    sort_order  INT          DEFAULT 0,
    created_at  DATETIME     DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='脚本目录';

-- 脚本表（包含所有增量字段：DS集成、模型增强、库名、告警通道）
CREATE TABLE IF NOT EXISTS dn_script (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    folder_id         BIGINT       NOT NULL COMMENT '所属目录ID',
    script_name       VARCHAR(200) NOT NULL COMMENT '脚本名称',
    script_type       VARCHAR(20)  NOT NULL COMMENT 'HiveSQL/Shell/Python/DataSync',
    database_name     VARCHAR(100) DEFAULT NULL COMMENT '所属数据库名',
    content           LONGTEXT     COMMENT '脚本内容',
    description       VARCHAR(500) DEFAULT '' COMMENT '描述',
    created_by        VARCHAR(50)  DEFAULT '' COMMENT '创建人',
    updated_by        VARCHAR(50)  DEFAULT '' COMMENT '更新人',
    created_at        DATETIME     DEFAULT CURRENT_TIMESTAMP,
    updated_at        DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    -- DolphinScheduler 集成字段
    ds_project_code   BIGINT       DEFAULT NULL COMMENT 'DS 项目 code',
    ds_workflow_code  BIGINT       DEFAULT NULL COMMENT 'DS 工作流 code',
    ds_task_code      BIGINT       DEFAULT NULL COMMENT 'DS 任务 code',
    ds_schedule_id    INT          DEFAULT NULL COMMENT 'DS 调度 ID',
    schedule_cron     VARCHAR(100) DEFAULT NULL COMMENT 'cron 表达式',
    schedule_status   VARCHAR(20)  DEFAULT 'offline' COMMENT '调度状态: offline/online',
    timeout_seconds   INT          DEFAULT 0    COMMENT '超时秒数',
    retry_times       INT          DEFAULT 0    COMMENT '重试次数',
    retry_interval    INT          DEFAULT 60   COMMENT '重试间隔(秒)',
    warning_type      VARCHAR(20)  DEFAULT 'NONE' COMMENT '告警类型: NONE/FAILURE/SUCCESS/ALL',
    -- 模型增强字段
    task_type         VARCHAR(32)  DEFAULT NULL COMMENT '任务类型(核心模型/核心扩展/看板模型/分析模型/重要应用/其他模型)',
    model_desc        TEXT         COMMENT '模型描述',
    subject           VARCHAR(64)  DEFAULT NULL COMMENT '主题域',
    sub_subject       VARCHAR(64)  DEFAULT NULL COMMENT '二级主题',
    -- 告警通道字段
    alert_channel     VARCHAR(64)  DEFAULT NULL COMMENT '告警通道',
    alert_contact     VARCHAR(256) DEFAULT NULL COMMENT '告警联系人',
    PRIMARY KEY (id),
    KEY idx_folder_id (folder_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='脚本表';

-- 脚本历史版本表（包含 version_type）
CREATE TABLE IF NOT EXISTS dn_script_version (
    id            BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    script_id     BIGINT   NOT NULL COMMENT '脚本ID',
    version       INT      NOT NULL DEFAULT 1 COMMENT '版本号',
    content       LONGTEXT COMMENT '脚本内容快照',
    commit_msg    VARCHAR(500) DEFAULT NULL COMMENT '提交说明',
    committed_by  VARCHAR(50)  DEFAULT NULL COMMENT '提交人',
    committed_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '提交时间',
    version_type  VARCHAR(16) DEFAULT 'save' COMMENT '版本类型(save/online)',
    PRIMARY KEY (id),
    KEY idx_script_id_committed_at (script_id, committed_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='脚本历史版本表';

-- 同步任务表（包含所有 DS 集成字段和告警通道）
CREATE TABLE IF NOT EXISTS dn_sync_task (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    task_name         VARCHAR(200) NOT NULL COMMENT '任务名称',
    source_ds_id      BIGINT       NOT NULL COMMENT '源数据源ID',
    source_db         VARCHAR(100) NOT NULL COMMENT '源库',
    source_table      VARCHAR(200) NOT NULL COMMENT '源表',
    target_db         VARCHAR(100) NOT NULL DEFAULT 'ods' COMMENT '目标库',
    target_table      VARCHAR(200) NOT NULL COMMENT '目标表',
    sync_mode         VARCHAR(20)  DEFAULT 'full' COMMENT 'full全量 / incr增量',
    partition_field   VARCHAR(50)  DEFAULT 'dt' COMMENT '分区字段',
    datax_json        LONGTEXT     COMMENT 'DataX JSON 配置',
    status            TINYINT      DEFAULT 1 COMMENT '1启用 0禁用',
    created_by        VARCHAR(50)  DEFAULT '',
    created_at        DATETIME     DEFAULT CURRENT_TIMESTAMP,
    updated_at        DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    -- DolphinScheduler 集成字段
    ds_project_code   BIGINT       DEFAULT NULL COMMENT 'DS项目code',
    ds_workflow_code  BIGINT       DEFAULT NULL COMMENT 'DS工作流code',
    ds_task_code      BIGINT       DEFAULT NULL COMMENT 'DS任务code',
    ds_schedule_id    INT          DEFAULT NULL COMMENT 'DS调度ID',
    schedule_cron     VARCHAR(64)  DEFAULT NULL COMMENT 'Cron表达式',
    schedule_status   VARCHAR(16)  DEFAULT NULL COMMENT '调度状态: online/offline',
    timeout_seconds   INT          DEFAULT 0    COMMENT '超时(秒)',
    retry_times       INT          DEFAULT 1    COMMENT '重试次数',
    retry_interval    INT          DEFAULT 60   COMMENT '重试间隔(秒)',
    warning_type      VARCHAR(16)  DEFAULT 'FAILURE' COMMENT '告警类型',
    -- 告警通道字段
    alert_channel     VARCHAR(64)  DEFAULT NULL COMMENT '告警通道',
    alert_contact     VARCHAR(256) DEFAULT NULL COMMENT '告警联系人',
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='同步任务';

-- 任务执行记录（统一存储所有类型任务的执行记录）
CREATE TABLE IF NOT EXISTS dn_task_execution (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    script_id       BIGINT       DEFAULT NULL COMMENT '关联 dn_script.id（SQL任务）',
    sync_task_id    BIGINT       DEFAULT NULL COMMENT '关联 dn_sync_task.id（同步任务）',
    task_type       VARCHAR(20)  NOT NULL COMMENT 'HiveSQL/Shell/DataSync',
    trigger_type    VARCHAR(20)  NOT NULL COMMENT 'manual手动 / schedule调度',
    ds_instance_id  BIGINT       DEFAULT NULL COMMENT 'DolphinScheduler 实例ID（调度触发时）',
    status          VARCHAR(20)  NOT NULL DEFAULT 'WAITING' COMMENT 'WAITING/RUNNING/SUCCESS/FAILED',
    start_time      DATETIME     DEFAULT NULL,
    end_time        DATETIME     DEFAULT NULL,
    duration        INT          DEFAULT 0 COMMENT '耗时（秒）',
    read_count      BIGINT       DEFAULT 0 COMMENT '读取条数（同步任务用）',
    write_count     BIGINT       DEFAULT 0 COMMENT '写入条数（同步任务用）',
    error_count     BIGINT       DEFAULT 0 COMMENT '错误条数',
    log             LONGTEXT     COMMENT '运行日志',
    executor        VARCHAR(50)  DEFAULT '' COMMENT '执行人',
    created_at      DATETIME     DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_script_id (script_id),
    KEY idx_sync_task_id (sync_task_id),
    KEY idx_status (status),
    KEY idx_start_time (start_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='任务执行记录';

-- 基线管理表
CREATE TABLE IF NOT EXISTS dn_baseline (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    baseline_name VARCHAR(200) NOT NULL COMMENT '基线名称',
    description   VARCHAR(500) COMMENT '基线描述',
    commit_time   TIME COMMENT '承诺完成时间',
    priority      INT DEFAULT 1 COMMENT '优先级 1=P1 2=P2 3=P3',
    status        VARCHAR(20) DEFAULT 'enabled' COMMENT 'enabled/disabled',
    created_by    VARCHAR(100) DEFAULT 'default',
    created_at    DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='基线管理';

-- 基线关联任务表
CREATE TABLE IF NOT EXISTS dn_baseline_task (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    baseline_id BIGINT NOT NULL COMMENT '基线ID',
    task_id     BIGINT NOT NULL COMMENT '任务ID',
    task_type   VARCHAR(20) NOT NULL COMMENT 'script/syncTask',
    task_name   VARCHAR(200) COMMENT '任务名称（冗余）',
    created_at  DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_baseline_id (baseline_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='基线关联任务';

-- 表评论
CREATE TABLE IF NOT EXISTS dn_table_comment (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    table_meta_id BIGINT NOT NULL COMMENT '关联 dn_table_meta.id',
    content       TEXT NOT NULL COMMENT '评论内容',
    created_by    VARCHAR(100) DEFAULT 'default',
    created_at    DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_table_meta_id (table_meta_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='表评论';

-- 任务依赖关系表（通过解析 SQL 自动计算）
CREATE TABLE IF NOT EXISTS dn_task_dependency (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id            BIGINT       NOT NULL COMMENT '下游任务ID',
    task_type          VARCHAR(16)  NOT NULL COMMENT 'script / syncTask',
    upstream_task_id   BIGINT       NOT NULL COMMENT '上游任务ID',
    upstream_task_type VARCHAR(16)  NOT NULL COMMENT 'script / syncTask',
    dep_table          VARCHAR(256) DEFAULT NULL COMMENT '产生依赖的表名',
    UNIQUE KEY uk_dep (task_id, task_type, upstream_task_id, upstream_task_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='任务依赖关系';

-- 每日调度运行记录表
CREATE TABLE IF NOT EXISTS dn_scheduler_run (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id       BIGINT       NOT NULL COMMENT '任务ID',
    task_type     VARCHAR(16)  NOT NULL COMMENT 'script / syncTask',
    run_date      DATE         NOT NULL COMMENT '数据日期（T-1）',
    run_type      VARCHAR(16)  DEFAULT 'daily' COMMENT 'daily=每日调度 / backfill=补数据',
    batch_id      VARCHAR(64)  DEFAULT NULL COMMENT '补数据批次ID',
    status        INT          DEFAULT 0 COMMENT '0=WAITING, 1=SUCCESS, 2=RUNNING, -1=FAILED, -2=PAUSED',
    start_time    DATETIME     DEFAULT NULL,
    end_time      DATETIME     DEFAULT NULL,
    log           LONGTEXT     DEFAULT NULL COMMENT '执行日志',
    retry_count   INT          DEFAULT 0,
    created_at    DATETIME     DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_task_date_type (task_id, task_type, run_date, run_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='调度运行记录';

-- 数据质量规则表
CREATE TABLE IF NOT EXISTS dn_quality_rule (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    rule_name     VARCHAR(200) NOT NULL COMMENT '规则名称',
    rule_type     VARCHAR(50)  NOT NULL COMMENT '规则类型: null_check/unique_check/value_range/regex_check/custom_sql',
    datasource_id BIGINT       NOT NULL COMMENT '数据源ID',
    database_name VARCHAR(100) NOT NULL COMMENT '数据库名',
    table_name    VARCHAR(200) NOT NULL COMMENT '表名',
    column_name   VARCHAR(200) DEFAULT NULL COMMENT '字段名(custom_sql时可空)',
    rule_config   TEXT COMMENT '规则配置JSON',
    custom_sql    TEXT COMMENT '自定义SQL(rule_type=custom_sql时使用)',
    severity      VARCHAR(20)  DEFAULT 'warning' COMMENT '严重级别: info/warning/error',
    status        INT          DEFAULT 1 COMMENT '状态: 1启用 0禁用',
    schedule_cron VARCHAR(100) DEFAULT NULL COMMENT '调度cron表达式',
    created_by    VARCHAR(50)  DEFAULT NULL,
    created_at    DATETIME     DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据质量规则';

-- 数据质量检查执行记录表
CREATE TABLE IF NOT EXISTS dn_quality_run (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    rule_id      BIGINT       NOT NULL COMMENT '规则ID',
    run_status   VARCHAR(20)  NOT NULL COMMENT '运行状态: success/failed/error',
    total_count  BIGINT       DEFAULT 0 COMMENT '总记录数',
    pass_count   BIGINT       DEFAULT 0 COMMENT '通过数',
    fail_count   BIGINT       DEFAULT 0 COMMENT '失败数',
    pass_rate    DECIMAL(5,2) DEFAULT 0 COMMENT '通过率(%)',
    error_sample TEXT COMMENT '异常样本(JSON数组,最多10条)',
    exec_sql     TEXT COMMENT '实际执行的SQL',
    duration_ms  BIGINT       DEFAULT 0 COMMENT '执行耗时(毫秒)',
    error_msg    TEXT COMMENT '错误信息',
    started_at   DATETIME     DEFAULT CURRENT_TIMESTAMP,
    finished_at  DATETIME     DEFAULT NULL,
    INDEX idx_rule_id (rule_id),
    INDEX idx_started_at (started_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据质量检查记录';

-- 表元数据（包含 view_count 和 row_count）
CREATE TABLE IF NOT EXISTS dn_table_meta (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    datasource_id BIGINT       NOT NULL COMMENT '数据源ID',
    database_name VARCHAR(100) NOT NULL COMMENT '数据库名',
    table_name    VARCHAR(200) NOT NULL COMMENT '表名',
    table_comment VARCHAR(500) DEFAULT NULL COMMENT '业务描述',
    owner         VARCHAR(50)  DEFAULT NULL COMMENT '负责人',
    tags          VARCHAR(500) DEFAULT NULL COMMENT '标签(逗号分隔)',
    importance    VARCHAR(20)  DEFAULT 'normal' COMMENT '重要性: core/important/normal',
    view_count    INT          DEFAULT 0 COMMENT '浏览次数',
    row_count     BIGINT       DEFAULT NULL COMMENT '表行数估算',
    created_at    DATETIME     DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_table (datasource_id, database_name, table_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='表元数据';

-- 字段元数据
CREATE TABLE IF NOT EXISTS dn_column_meta (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    table_meta_id BIGINT       NOT NULL COMMENT '表元数据ID',
    column_name   VARCHAR(200) NOT NULL COMMENT '字段名',
    business_name VARCHAR(200) DEFAULT NULL COMMENT '业务名称',
    business_desc VARCHAR(500) DEFAULT NULL COMMENT '业务描述',
    tags          VARCHAR(500) DEFAULT NULL COMMENT '标签(逗号分隔)',
    created_at    DATETIME     DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_column (table_meta_id, column_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='字段元数据';

-- 指标定义表
CREATE TABLE IF NOT EXISTS dn_metric (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    metric_name  VARCHAR(200) NOT NULL COMMENT '指标名称',
    metric_code  VARCHAR(100) NOT NULL COMMENT '指标编码(唯一)',
    category     VARCHAR(100) DEFAULT NULL COMMENT '指标分类',
    description  TEXT COMMENT '指标描述/业务口径',
    calc_formula TEXT COMMENT '计算公式/SQL',
    data_source  VARCHAR(200) DEFAULT NULL COMMENT '数据来源(库.表)',
    dimensions   VARCHAR(500) DEFAULT NULL COMMENT '统计维度(逗号分隔)',
    unit         VARCHAR(50)  DEFAULT NULL COMMENT '单位(元/次/人等)',
    owner        VARCHAR(50)  DEFAULT NULL COMMENT '负责人',
    status       INT          DEFAULT 1 COMMENT '状态: 1启用 0废弃',
    tags         VARCHAR(500) DEFAULT NULL COMMENT '标签(逗号分隔)',
    created_at   DATETIME     DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_code (metric_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='指标定义';

-- 主题域配置表
CREATE TABLE IF NOT EXISTS dn_subject (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    name       VARCHAR(64) NOT NULL COMMENT '主题名称',
    parent_id  BIGINT      DEFAULT NULL COMMENT '父主题ID(NULL表示一级主题)',
    layer      VARCHAR(16) DEFAULT 'ALL' COMMENT '适用分层(DWD/DIM/DWS/ADS/ALL)',
    sort_order INT         DEFAULT 0,
    created_at DATETIME    DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='主题域配置';

-- 分组表
CREATE TABLE IF NOT EXISTS dn_group (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    group_name  VARCHAR(64)  NOT NULL COMMENT '分组名称',
    description VARCHAR(256) DEFAULT NULL COMMENT '分组描述',
    admin_user  VARCHAR(64)  NOT NULL COMMENT '管理员',
    created_at  DATETIME     DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='告警分组';

-- 分组成员表
CREATE TABLE IF NOT EXISTS dn_group_member (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    group_id   BIGINT      NOT NULL COMMENT '分组ID',
    username   VARCHAR(64) NOT NULL COMMENT '用户名',
    role       VARCHAR(16) DEFAULT 'member' COMMENT '角色(admin/member)',
    created_at DATETIME    DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_group_user (group_id, username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='分组成员';

-- 告警配置表
CREATE TABLE IF NOT EXISTS dn_alert_config (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    script_id           BIGINT       NOT NULL COMMENT '关联脚本ID',
    alert_types         VARCHAR(256) DEFAULT '["failure"]' COMMENT '告警类型JSON数组(failure/delay/quality)',
    delay_threshold_min INT          DEFAULT NULL COMMENT '延迟告警阈值(分钟)',
    quality_rule_ids    VARCHAR(512) DEFAULT NULL COMMENT '关联质量规则ID,逗号分隔',
    alert_scope         VARCHAR(16)  DEFAULT 'personal' COMMENT '告警范围(personal/group)',
    group_id            BIGINT       DEFAULT NULL COMMENT '告警分组ID',
    enabled             TINYINT      DEFAULT 1 COMMENT '是否启用',
    created_at          DATETIME     DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_script (script_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='告警配置';

-- 表收藏
CREATE TABLE IF NOT EXISTS dn_table_favorite (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    database_name VARCHAR(100) NOT NULL COMMENT '数据库名',
    table_name    VARCHAR(200) NOT NULL COMMENT '表名',
    created_by    VARCHAR(100) DEFAULT 'default',
    created_at    DATETIME     DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_fav (database_name, table_name, created_by)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='表收藏';

-- 搜索历史
CREATE TABLE IF NOT EXISTS dn_search_history (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    database_name VARCHAR(100) NOT NULL COMMENT '数据库名',
    table_name    VARCHAR(200) NOT NULL COMMENT '表名',
    created_by    VARCHAR(100) DEFAULT 'default',
    searched_at   DATETIME     DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_search (database_name, table_name, created_by)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='搜索历史';

-- 系统配置表（AI配置等全局设置）
CREATE TABLE IF NOT EXISTS dn_system_config (
    config_key   VARCHAR(100) NOT NULL PRIMARY KEY COMMENT '配置键',
    config_value TEXT COMMENT '配置值（敏感信息加密存储）',
    description  VARCHAR(200) COMMENT '配置说明',
    updated_at   DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统配置表';


-- ============================================================
-- 示例数据
-- ============================================================

-- 初始化地区数据
USE base_data;

INSERT INTO base_region (id, region_name) VALUES
(1, '华北'), (2, '华东'), (3, '华南'), (4, '华中'),
(5, '西南'), (6, '西北'), (7, '东北');

INSERT INTO base_province (id, name, region_id, area_code, iso_code) VALUES
(1,  '北京', 1, '010', 'CN-BJ'),
(2,  '天津', 1, '022', 'CN-TJ'),
(3,  '河北', 1, '0311', 'CN-HE'),
(4,  '山西', 1, '0351', 'CN-SX'),
(5,  '内蒙古', 1, '0471', 'CN-NM'),
(6,  '上海', 2, '021', 'CN-SH'),
(7,  '江苏', 2, '025', 'CN-JS'),
(8,  '浙江', 2, '0571', 'CN-ZJ'),
(9,  '安徽', 2, '0551', 'CN-AH'),
(10, '福建', 2, '0591', 'CN-FJ'),
(11, '江西', 2, '0791', 'CN-JX'),
(12, '山东', 2, '0531', 'CN-SD'),
(13, '广东', 3, '020', 'CN-GD'),
(14, '广西', 3, '0771', 'CN-GX'),
(15, '海南', 3, '0898', 'CN-HI'),
(16, '河南', 4, '0371', 'CN-HA'),
(17, '湖北', 4, '027', 'CN-HB'),
(18, '湖南', 4, '0731', 'CN-HN'),
(19, '重庆', 5, '023', 'CN-CQ'),
(20, '四川', 5, '028', 'CN-SC'),
(21, '贵州', 5, '0851', 'CN-GZ'),
(22, '云南', 5, '0871', 'CN-YN'),
(23, '西藏', 5, '0891', 'CN-XZ'),
(24, '陕西', 6, '029', 'CN-SN'),
(25, '甘肃', 6, '0931', 'CN-GS'),
(26, '青海', 6, '0971', 'CN-QH'),
(27, '宁夏', 6, '0951', 'CN-NX'),
(28, '新疆', 6, '0991', 'CN-XJ'),
(29, '辽宁', 7, '024', 'CN-LN'),
(30, '吉林', 7, '0431', 'CN-JL'),
(31, '黑龙江', 7, '0451', 'CN-HL'),
(32, '台湾', 2, '886', 'CN-TW'),
(33, '香港', 3, '852', 'CN-HK'),
(34, '澳门', 3, '853', 'CN-MO');

-- 初始化数据字典
INSERT INTO base_dic (dic_type, dic_code, dic_name, sort_order) VALUES
-- 订单状态
('order_status', '未支付', '未支付', 1),
('order_status', '已支付', '已支付', 2),
('order_status', '已发货', '已发货', 3),
('order_status', '已签收', '已签收', 4),
('order_status', '已完成', '已完成', 5),
('order_status', '已取消', '已取消', 6),
-- 子订单状态
('sub_order_status', '待付款', '待付款', 1),
('sub_order_status', '已付款', '已付款', 2),
('sub_order_status', '已出库', '已出库', 3),
('sub_order_status', '已发货', '已发货', 4),
('sub_order_status', '已签收', '已签收', 5),
('sub_order_status', '已完成', '已完成', 6),
('sub_order_status', '已取消', '已取消', 7),
-- 支付方式
('pay_type', 'WECHAT', '微信支付', 1),
('pay_type', 'ALIPAY', '支付宝', 2),
('pay_type', 'BALANCE', '余额支付', 3),
('pay_type', 'UNION', '银联支付', 4),
-- 支付状态
('payment_status', '未支付', '未支付', 1),
('payment_status', '支付中', '支付中', 2),
('payment_status', '已支付', '已支付', 3),
('payment_status', '支付失败', '支付失败', 4),
-- 退款类型
('refund_type', '仅退款', '仅退款', 1),
('refund_type', '退货退款', '退货退款', 2),
-- 退款状态
('refund_status', '申请中', '申请中', 1),
('refund_status', '已同意', '已同意', 2),
('refund_status', '已拒绝', '已拒绝', 3),
('refund_status', '退货中', '退货中', 4),
('refund_status', '已退款', '已退款', 5),
('refund_status', '已关闭', '已关闭', 6),
-- 退款原因
('refund_reason', '质量问题', '质量问题', 1),
('refund_reason', '发错货', '发错货', 2),
('refund_reason', '不想要了', '不想要了', 3),
('refund_reason', '少件/漏发', '少件/漏发', 4),
('refund_reason', '与描述不符', '与描述不符', 5),
('refund_reason', '其他', '其他', 6),
-- 快递公司
('express_company', 'SF', '顺丰速运', 1),
('express_company', 'YTO', '圆通速递', 2),
('express_company', 'ZTO', '中通快递', 3),
('express_company', 'STO', '申通快递', 4),
('express_company', 'JD', '京东物流', 5),
('express_company', 'YD', '韵达快递', 6),
-- 物流状态
('delivery_status', '已揽收', '已揽收', 1),
('delivery_status', '运输中', '运输中', 2),
('delivery_status', '派送中', '派送中', 3),
('delivery_status', '已签收', '已签收', 4),
('delivery_status', '异常', '异常', 5),
-- 订单类型
('order_type', '普通', '普通订单', 1),
('order_type', '预售', '预售订单', 2),
('order_type', '秒杀', '秒杀订单', 3),
('order_type', '拼团', '拼团订单', 4),
-- 优惠券类型
('coupon_type', '满减', '满减券', 1),
('coupon_type', '折扣', '折扣券', 2),
('coupon_type', '无门槛', '无门槛券', 3),
('coupon_type', '运费券', '运费券', 4),
-- 活动类型
('activity_type', '满减', '满减活动', 1),
('activity_type', '折扣', '折扣活动', 2),
('activity_type', '秒杀', '秒杀活动', 3),
-- 性别
('gender', 'M', '男', 1),
('gender', 'F', '女', 2),
('gender', 'U', '未知', 3),
-- 用户等级
('user_level', '普通', '普通会员', 1),
('user_level', '铜牌', '铜牌会员', 2),
('user_level', '银牌', '银牌会员', 3),
('user_level', '金牌', '金牌会员', 4),
('user_level', '钻石', '钻石会员', 5),
-- 仓库类型
('warehouse_type', '自营仓', '自营仓', 1),
('warehouse_type', '前置仓', '前置仓', 2),
('warehouse_type', '保税仓', '保税仓', 3),
-- 库存变动类型
('stock_change_type', '入库', '入库', 1),
('stock_change_type', '锁定', '下单锁定', 2),
('stock_change_type', '扣减', '支付扣减', 3),
('stock_change_type', '释放', '取消释放', 4),
('stock_change_type', '盘点调整', '盘点调整', 5),
-- 订单来源
('order_source', 'PC', 'PC端', 1),
('order_source', 'APP', 'APP端', 2),
('order_source', 'H5', 'H5端', 3),
('order_source', '小程序', '微信小程序', 4),
-- 设备类型
('device_type', 'iOS', 'iOS', 1),
('device_type', 'Android', 'Android', 2),
('device_type', 'PC', 'PC', 3),
-- 评价
('rating', '1', '好评', 1),
('rating', '2', '中评', 2),
('rating', '3', '差评', 3);

USE datanote;

-- 预置常用主题域
INSERT INTO dn_subject (name, parent_id, layer, sort_order) VALUES
('交易', NULL, 'ALL', 1),
('用户', NULL, 'ALL', 2),
('商品', NULL, 'ALL', 3),
('营销', NULL, 'ALL', 4),
('仓储物流', NULL, 'ALL', 5),
('财务', NULL, 'ALL', 6);

-- 二级主题示例
INSERT INTO dn_subject (name, parent_id, layer, sort_order) VALUES
('订单', 1, 'ALL', 1),
('支付', 1, 'ALL', 2),
('退款', 1, 'ALL', 3),
('会员', 2, 'ALL', 1),
('行为', 2, 'ALL', 2),
('画像', 2, 'ALL', 3);

-- ============================================================
-- DataNote 默认数据（文件夹 + 数据源 + 同步任务 + 示例脚本）
-- ============================================================
USE datanote;

-- 默认数据源：本地 MySQL（Docker 环境指向容器内 MySQL）
INSERT INTO dn_datasource (name, type, host, port, username, password, database_name, status, created_by)
VALUES ('本地 MySQL', 'MySQL', 'mysql', 3306, 'root', 'datanote123', '', 1, 'admin');

-- 默认文件夹结构（parent_id=0 表示顶级）
INSERT INTO dn_script_folder (folder_name, parent_id, layer, sort_order) VALUES
('数据源', 0, '数据源', -1),
('ODS 层', 0, 'ODS', 0),
('DWD 层', 0, 'DWD', 3),
('DWS 层', 0, 'DWS', 4),
('ADS 层', 0, 'ADS', 5),
('脚本',   0, '脚本', 6);

-- ODS 同步任务（电商示例表，source_ds_id=1 对应上面的数据源）
INSERT INTO dn_sync_task (task_name, source_ds_id, source_db, source_table, target_db, target_table, sync_mode, partition_field, schedule_cron, schedule_status, warning_type, retry_times, retry_interval, timeout_seconds) VALUES
('ods_order_center_order_info_df',       1, 'order_center', 'order_info',       'ods', 'ods_order_center_order_info_df',       'df', 'dt', '0 0 2 * * ?', 'online', 'FAILURE', 1, 60, 3600),
('ods_order_center_order_detail_df',     1, 'order_center', 'order_detail',     'ods', 'ods_order_center_order_detail_df',     'df', 'dt', '0 0 2 * * ?', 'online', 'FAILURE', 1, 60, 3600),
('ods_order_center_order_status_log_df', 1, 'order_center', 'order_status_log', 'ods', 'ods_order_center_order_status_log_df', 'df', 'dt', '0 0 2 * * ?', 'online', 'FAILURE', 1, 60, 3600),
('ods_order_center_cart_info_df',        1, 'order_center', 'cart_info',        'ods', 'ods_order_center_cart_info_df',        'df', 'dt', '0 0 2 * * ?', 'online', 'FAILURE', 1, 60, 3600),
('ods_order_center_order_refund_info_df',1, 'order_center', 'order_refund_info','ods', 'ods_order_center_order_refund_info_df','df', 'dt', '0 0 2 * * ?', 'online', 'FAILURE', 1, 60, 3600),
('ods_order_center_order_sub_df',        1, 'order_center', 'order_sub',        'ods', 'ods_order_center_order_sub_df',        'df', 'dt', '0 0 2 * * ?', 'online', 'FAILURE', 1, 60, 3600);

-- DWD 示例脚本（folder_id=3 对应 DWD 层）
INSERT INTO dn_script (folder_id, script_name, script_type, database_name, content, task_type, model_desc, subject, schedule_cron, schedule_status, warning_type, retry_times, retry_interval, timeout_seconds) VALUES
(3, 'dwd_trad_order_detail_df', 'hive', 'dwd',
'-- DWD 交易订单明细宽表\n-- 粒度: 一行 = 一个订单明细（SKU 级别）\n\nINSERT OVERWRITE TABLE dwd.dwd_trad_order_detail_df PARTITION (dt = ''${bizdate}'')\nSELECT\n     t1.id AS order_id\n    ,t1.order_no\n    ,t1.user_id\n    ,t2.id AS detail_id\n    ,t2.sku_id\n    ,t2.sku_name\n    ,t2.sku_num\n    ,t2.unit_price\n    ,t2.split_total_amount\n    ,t1.total_amount\n    ,t1.pay_amount\n    ,t1.pay_type\n    ,t1.order_status\n    ,t1.create_time\nFROM ods.ods_order_center_order_info_df t1\nLEFT JOIN ods.ods_order_center_order_detail_df t2\nON t1.id = t2.order_id\nWHERE t1.dt = ''${bizdate}''\nAND t2.dt = ''${bizdate}''\n;',
'核心模型', 'DWD交易订单明细宽表，关联主订单和订单明细，SKU粒度', '交易',
'0 0 2 * * ?', 'online', 'FAILURE', 1, 60, 3600);
