-- 取数需求工作项（参考飞书 Meego，面向数仓取数 + AI）
-- 主表：一条业务取数需求，走状态流 clarifying→reviewing→confirmed→querying→checking→delivered→closed；rejected
CREATE TABLE IF NOT EXISTS dn_requirement (
    id             BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '需求ID',
    title          VARCHAR(200) NOT NULL COMMENT '需求标题',
    description    TEXT         DEFAULT NULL COMMENT '业务原始描述（原话）',
    biz_domain     VARCHAR(50)  DEFAULT NULL COMMENT '业务域（授信/放款/风控…）',
    priority       VARCHAR(10)  DEFAULT '中' COMMENT '优先级：高/中/低',
    submitter_id   BIGINT       DEFAULT NULL COMMENT '提出人ID（关联 dn_user）',
    submitter_name VARCHAR(50)  DEFAULT NULL COMMENT '提出人',
    assignee_name  VARCHAR(50)  DEFAULT NULL COMMENT '处理人（数仓/AI）',
    status         VARCHAR(20)  DEFAULT 'clarifying' COMMENT '状态',
    session_id     VARCHAR(64)  DEFAULT NULL COMMENT '关联的 AI 澄清对话（req-{id}）',
    spec           LONGTEXT     DEFAULT NULL COMMENT '加工口径+建模建议（JSON）',
    sql_text       LONGTEXT     DEFAULT NULL COMMENT '生成的 SQL',
    result_summary LONGTEXT     DEFAULT NULL COMMENT '出数结果摘要',
    check_result   LONGTEXT     DEFAULT NULL COMMENT '核对结论',
    created_at     DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='取数需求工作项';

-- 流转/操作记录表：需求的每一次状态变化、每一句评论（Meego 的「动态」）
CREATE TABLE IF NOT EXISTS dn_requirement_log (
    id          BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '记录ID',
    req_id      BIGINT       NOT NULL COMMENT '需求ID',
    from_status VARCHAR(20)  DEFAULT NULL COMMENT '原状态（创建时为空）',
    to_status   VARCHAR(20)  DEFAULT NULL COMMENT '新状态',
    operator    VARCHAR(50)  DEFAULT NULL COMMENT '操作人',
    comment     VARCHAR(500) DEFAULT NULL COMMENT '备注/评论',
    created_at  DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '时间',
    KEY idx_req (req_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='取数需求流转记录';
