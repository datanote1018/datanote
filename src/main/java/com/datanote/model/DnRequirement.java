package com.datanote.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 取数需求工作项 — 对应 dn_requirement 表
 * <p>
 * 一条需求走状态流：clarifying(澄清) → reviewing(待评审) → confirmed(已确认)
 * → querying(出数中) → checking(核对中) → delivered(已交付) → closed(已关闭)；rejected(已驳回)
 */
@Data
@TableName("dn_requirement")
public class DnRequirement {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String title;
    private String description;
    private String bizDomain;
    private String priority;
    private Long submitterId;
    private String submitterName;
    private String assigneeName;
    private String status;
    private String sessionId;
    /** 加工口径+建模建议（JSON 字符串） */
    private String spec;
    private String sqlText;
    private String resultSummary;
    private String checkResult;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
