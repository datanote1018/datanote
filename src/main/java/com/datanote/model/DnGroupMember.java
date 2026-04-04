package com.datanote.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 分组成员实体 — 对应 dn_group_member 表
 */
@Data
@TableName("dn_group_member")
public class DnGroupMember {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long groupId;
    private String username;
    private String role;
    private LocalDateTime createdAt;
}
