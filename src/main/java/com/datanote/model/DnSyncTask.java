package com.datanote.model;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 同步任务实体 — 对应 dn_sync_task 表
 */
@Data
@TableName("dn_sync_task")
public class DnSyncTask {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String taskName;
    private Long sourceDsId;
    private String sourceDb;
    private String sourceTable;
    private String targetDb;
    private String targetTable;
    private String syncMode;
    private String partitionField;
    private String dataxJson;
    private Integer status;
    private String createdBy;
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
    private String alertChannel;
    private String alertContact;
}
