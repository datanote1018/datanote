package com.datanote.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.datanote.mapper.DnTaskExecutionMapper;
import com.datanote.model.DnTaskExecution;
import com.datanote.model.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/task-execution")
@RequiredArgsConstructor
public class TaskExecutionController {

    private final DnTaskExecutionMapper taskExecutionMapper;

    @GetMapping("/list")
    public R<List<DnTaskExecution>> list(@RequestParam Long taskId,
                                          @RequestParam String taskType,
                                          @RequestParam(defaultValue = "20") int limit) {
        QueryWrapper<DnTaskExecution> qw = new QueryWrapper<>();
        if ("script".equals(taskType)) {
            qw.eq("script_id", taskId);
        } else {
            qw.eq("sync_task_id", taskId);
        }
        qw.orderByDesc("start_time").last("LIMIT " + limit);
        return R.ok(taskExecutionMapper.selectList(qw));
    }

    @GetMapping("/{id}")
    public R<DnTaskExecution> detail(@PathVariable Long id) {
        return R.ok(taskExecutionMapper.selectById(id));
    }
}
