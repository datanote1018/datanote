package com.datanote.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户实体 — 对应 dn_user 表
 */
@Data
@TableName("dn_user")
public class DnUser {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 登录名 */
    private String username;
    /** bcrypt 加密后的密码 */
    private String password;
    /** 昵称 */
    private String nickname;
    /** 角色：ADMIN / USER */
    private String role;
    /** 状态：1 启用 0 禁用 */
    private Integer status;
    private LocalDateTime createdAt;
}
