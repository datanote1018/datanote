package com.datanote.model;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 脚本文件夹实体 — 对应 dn_script_folder 表
 */
@Data
@TableName("dn_script_folder")
public class DnScriptFolder {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String folderName;
    private Long parentId;
    private String layer;
    private Integer sortOrder;
    private LocalDateTime createdAt;
}
