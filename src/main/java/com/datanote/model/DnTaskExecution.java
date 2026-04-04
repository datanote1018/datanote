package com.datanote.model;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 任务执行记录实体 — 对应 dn_task_execution 表
 */
@Data
@TableName("dn_task_execution")
public class DnTaskExecution {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long scriptId;
    private Long syncTaskId;
    private String taskType;
    private String triggerType;
    private Long dsInstanceId;
    private String status;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer duration;
    private Long readCount;
    private Long writeCount;
    private Long errorCount;
    private String log;
    private String executor;
    private LocalDateTime createdAt;
}
