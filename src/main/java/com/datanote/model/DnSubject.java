package com.datanote.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 主题域实体 — 对应 dn_subject 表
 */
@Data
@TableName("dn_subject")
public class DnSubject {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private Long parentId;
    private String layer;
    private Integer sortOrder;
    private LocalDateTime createdAt;
}
