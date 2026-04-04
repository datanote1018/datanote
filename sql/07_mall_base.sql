-- ============================================
-- 基础数据库 base_data
-- 负责：省份地区、数据字典、评价、SKU成本
-- 这些是跨业务域的公共数据，独立建库
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

-- ============================================
-- 初始化省份和地区数据
-- ============================================

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

-- ============================================
-- 初始化数据字典
-- ============================================

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
