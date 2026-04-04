package com.datanote.model;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

/**
 * 任务依赖关系实体 — 对应 dn_task_dependency 表
 */
@Data
@TableName("dn_task_dependency")
public class DnTaskDependency {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long taskId;
    private String taskType;
    private Long upstreamTaskId;
    private String upstreamTaskType;
    private String depTable;
}
