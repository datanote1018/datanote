package com.datanote.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 分组实体 — 对应 dn_group 表
 */
@Data
@TableName("dn_group")
public class DnGroup {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String groupName;
    private String description;
    private String adminUser;
    private LocalDateTime createdAt;
}
