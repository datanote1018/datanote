-- 主题域配置表
CREATE TABLE IF NOT EXISTS dn_subject (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(64) NOT NULL COMMENT '主题名称',
  parent_id BIGINT DEFAULT NULL COMMENT '父主题ID(NULL表示一级主题)',
  layer VARCHAR(16) DEFAULT 'ALL' COMMENT '适用分层(DWD/DIM/DWS/ADS/ALL)',
  sort_order INT DEFAULT 0,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='主题域配置';

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
