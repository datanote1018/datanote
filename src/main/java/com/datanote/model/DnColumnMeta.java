package com.datanote.model;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 字段元数据实体 — 对应 dn_column_meta 表
 */
@Data
@TableName("dn_column_meta")
public class DnColumnMeta {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tableMetaId;
    private String columnName;
    private String businessName;
    private String businessDesc;
    private String tags;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
