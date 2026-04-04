package com.datanote.model;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 调度运行记录实体 — 对应 dn_scheduler_run 表
 */
@Data
@TableName("dn_scheduler_run")
public class DnSchedulerRun {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long taskId;
    private String taskType;
    private LocalDate runDate;
    private String runType;
    private String batchId;
    private Integer status;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String log;
    private Integer retryCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // 状态常量
    public static final int STATUS_WAITING = 0;
    public static final int STATUS_SUCCESS = 1;
    public static final int STATUS_RUNNING = 2;
    public static final int STATUS_FAILED = -1;
    public static final int STATUS_PAUSED = -2;
}
