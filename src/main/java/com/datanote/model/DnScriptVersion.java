package com.datanote.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 脚本版本实体 — 对应 dn_script_version 表
 */
@Data
@TableName("dn_script_version")
public class DnScriptVersion {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long scriptId;
    private Integer version;
    private String content;
    private String commitMsg;
    private String committedBy;
    private LocalDateTime committedAt;
    private String versionType;
}
