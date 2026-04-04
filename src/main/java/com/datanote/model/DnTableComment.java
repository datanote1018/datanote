package com.datanote.model;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 表评论实体 — 对应 dn_table_comment 表
 */
@Data
@TableName("dn_table_comment")
public class DnTableComment {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tableMetaId;
    private String content;
    private String createdBy;
    private LocalDateTime createdAt;
}
