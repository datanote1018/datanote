package com.datanote.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 取数需求流转记录 — 对应 dn_requirement_log 表
 * <p>记录一条需求的每一次状态变化与评论（Meego 的「动态」），保证全程可追溯。
 */
@Data
@TableName("dn_requirement_log")
public class DnRequirementLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long reqId;
    private String fromStatus;
    private String toStatus;
    private String operator;
    private String comment;
    private LocalDateTime createdAt;
}
