package com.datanote.model;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 基线任务关联实体 — 对应 dn_baseline_task 表
 */
@Data
@TableName("dn_baseline_task")
public class DnBaselineTask {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long baselineId;
    private Long taskId;
    private String taskType;
    private String taskName;
    private LocalDateTime createdAt;
}
