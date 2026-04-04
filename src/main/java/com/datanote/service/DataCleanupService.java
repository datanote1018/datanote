package com.datanote.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.datanote.mapper.DnSchedulerRunMapper;
import com.datanote.mapper.DnTaskExecutionMapper;
import com.datanote.model.DnSchedulerRun;
import com.datanote.model.DnTaskExecution;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 数据清理服务 — 定时清理超过 30 天的执行记录
 */
@Service
@RequiredArgsConstructor
public class DataCleanupService {

    private static final Logger log = LoggerFactory.getLogger(DataCleanupService.class);
    private static final int RETENTION_DAYS = 30;

    private final DnSchedulerRunMapper schedulerRunMapper;
    private final DnTaskExecutionMapper taskExecutionMapper;

    /**
     * 每天凌晨 2:00 清理超过 30 天的数据
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void cleanupExpiredData() {
        LocalDate cutoffDate = LocalDate.now().minusDays(RETENTION_DAYS);
        LocalDateTime cutoffDateTime = cutoffDate.atStartOfDay();

        // 清理 dn_scheduler_run
        QueryWrapper<DnSchedulerRun> runQw = new QueryWrapper<>();
        runQw.lt("run_date", cutoffDate);
        int deletedRuns = schedulerRunMapper.delete(runQw);

        // 清理 dn_task_execution
        QueryWrapper<DnTaskExecution> execQw = new QueryWrapper<>();
        execQw.lt("created_at", cutoffDateTime);
        int deletedExecs = taskExecutionMapper.delete(execQw);

        if (deletedRuns > 0 || deletedExecs > 0) {
            log.info("数据清理完成: 删除 {} 条调度记录, {} 条执行指标 (截止 {})",
                    deletedRuns, deletedExecs, cutoffDate);
        }
    }
}
