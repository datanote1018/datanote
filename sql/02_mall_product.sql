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
