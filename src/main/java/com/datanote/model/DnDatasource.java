package com.datanote.model;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 数据源实体 — 对应 dn_datasource 表
 */
@Data
@TableName("dn_datasource")
public class DnDatasource {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String type;
    private String host;
    private Integer port;
    private String databaseName;
    private String username;
    private String password;
    private String extraParams;
    private Integer status;
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
