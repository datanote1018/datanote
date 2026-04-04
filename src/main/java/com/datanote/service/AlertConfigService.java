package com.datanote.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.datanote.mapper.DnAlertConfigMapper;
import com.datanote.mapper.DnSchedulerRunMapper;
import com.datanote.model.DnAlertConfig;
import com.datanote.model.DnSchedulerRun;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 告警配置服务 — 管理脚本的告警规则与延迟阈值计算
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlertConfigService {

    private final DnAlertConfigMapper alertConfigMapper;
    private final DnSchedulerRunMapper runMapper;

    /**
     * 根据脚本 ID 获取告警配置，不存在则创建默认配置
     *
     * @param scriptId 脚本 ID
     * @return 告警配置
     */
    public DnAlertConfig getByScriptId(Long scriptId) {
        QueryWrapper<DnAlertConfig> qw = new QueryWrapper<>();
        qw.eq("script_id", scriptId);
        DnAlertConfig config = alertConfigMapper.selectOne(qw);
        if (config == null) {
            config = new DnAlertConfig();
            config.setScriptId(scriptId);
            config.setAlertTypes("[\"delay\"]");
            config.setDelayThresholdMin(60);
            config.setQualityRuleIds("");
            config.setAlertScope("personal");
            config.setEnabled(1);
            config.setCreatedAt(LocalDateTime.now());
            config.setUpdatedAt(LocalDateTime.now());
            alertConfigMapper.insert(config);
        }
        return config;
    }

    /**
     * 保存告警配置（新增或更新）
     *
     * @param config 告警配置
     * @return 保存后的配置
     */
    public DnAlertConfig save(DnAlertConfig config) {
        if (config.getId() != null) {
            config.setUpdatedAt(LocalDateTime.now());
            alertConfigMapper.updateById(config);
        } else {
            config.setCreatedAt(LocalDateTime.now());
            config.setUpdatedAt(LocalDateTime.now());
            alertConfigMapper.insert(config);
        }
        return config;
    }

    /**
     * 自动计算延迟阈值：查询最近 7 天成功运行记录的平均耗时 + 40 分钟
     *
     * @param scriptId 脚本 ID
     * @return 延迟阈值（分钟）
     */
    public int calculateDelayThreshold(Long scriptId) {
        QueryWrapper<DnSchedulerRun> qw = new QueryWrapper<>();
        qw.eq("task_id", scriptId)
          .eq("task_type", "script")
          .eq("status", 1)
          .ge("start_time", LocalDateTime.now().minusDays(7));
        List<DnSchedulerRun> runs = runMapper.selectList(qw);

        if (runs.isEmpty()) {
            return 60;
        }

        long totalMinutes = 0;
        int validCount = 0;
        for (DnSchedulerRun run : runs) {
            if (run.getStartTime() != null && run.getEndTime() != null) {
                long minutes = Duration.between(run.getStartTime(), run.getEndTime()).toMinutes();
                totalMinutes += minutes;
                validCount++;
            }
        }

        if (validCount == 0) {
            return 60;
        }

        int avgMinutes = (int) (totalMinutes / validCount);
        return avgMinutes + 40;
    }
}
