package com.datanote.model;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("dn_table_favorite")
public class DnTableFavorite {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String databaseName;
    private String tableName;
    private String createdBy;
    private LocalDateTime createdAt;
}
