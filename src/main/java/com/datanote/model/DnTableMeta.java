package com.datanote.model;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 表元数据实体 — 对应 dn_table_meta 表
 */
@Data
@TableName("dn_table_meta")
public class DnTableMeta {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long datasourceId;
    private String databaseName;
    private String tableName;
    private String tableComment;
    private String owner;
    private String tags;
    private String importance;
    private Integer viewCount;
    private Long rowCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
