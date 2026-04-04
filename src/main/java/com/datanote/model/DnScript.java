package com.datanote.model;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 脚本实体 — 对应 dn_script 表
 */
@Data
@TableName("dn_script")
public class DnScript {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long folderId;
    private String scriptName;
    private String scriptType;
    private String databaseName;
    private String content;
    private String description;
    private String createdBy;
    private String updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // DolphinScheduler 集成字段
    private Long dsProjectCode;
    private Long dsWorkflowCode;
    private Long dsTaskCode;
    private Integer dsScheduleId;
    private String scheduleCron;
    private String scheduleStatus;
    private Integer timeoutSeconds;
    private Integer retryTimes;
    private Integer retryInterval;
    private String warningType;

    private String taskType;
    private String modelDesc;
    private String subject;
    private String subSubject;
    private String alertChannel;
    private String alertContact;
}
