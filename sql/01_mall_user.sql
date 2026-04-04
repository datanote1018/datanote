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
