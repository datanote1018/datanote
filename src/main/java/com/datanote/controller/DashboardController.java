package com.datanote.controller;

import com.datanote.common.Constants;
import com.datanote.model.R;
import com.datanote.model.DnScript;
import com.datanote.model.DnSchedulerRun;
import com.datanote.model.DnSyncTask;
import com.datanote.mapper.DnSyncTaskMapper;
import com.datanote.mapper.DnScriptMapper;
import com.datanote.mapper.DnSchedulerRunMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.LocalDate;
import java.util.*;

/**
 * 仪表盘 Controller — 首页统计数据 & 服务状态检测
 */
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
@Tag(name = "仪表盘", description = "首页统计数据与服务状态检测")
public class DashboardController {

    private final DnSyncTaskMapper syncTaskMapper;
    private final DnScriptMapper scriptMapper;
    private final DnSchedulerRunMapper schedulerRunMapper;

    /**
     * 首页统计数据
     */
    @Operation(summary = "首页统计数据")
    @GetMapping("/stats")
    public R<Map<String, Object>> stats(@RequestParam(required = false) Boolean myTask) {
        Map<String, Object> data = new HashMap<>();

        // 当 myTask=true 时，按 created_by='default' 过滤（单用户模式）
        String currentUser = "default";

        QueryWrapper<DnScript> scriptQw = new QueryWrapper<>();
        if (Boolean.TRUE.equals(myTask)) {
            scriptQw.eq("created_by", currentUser);
        }
        long scriptCount = scriptMapper.selectCount(scriptQw);

        QueryWrapper<DnSyncTask> syncQw = new QueryWrapper<>();
        // syncTask 暂无 createdBy，不按用户过滤
        long syncTaskCount = syncTaskMapper.selectCount(null);
        data.put("scriptCount", scriptCount);
        data.put("syncTaskCount", syncTaskCount);

        QueryWrapper<DnScript> onlineScriptQw = new QueryWrapper<>();
        onlineScriptQw.eq("schedule_status", Constants.SCHEDULE_ONLINE);
        if (Boolean.TRUE.equals(myTask)) {
            onlineScriptQw.eq("created_by", currentUser);
        }
        long onlineScriptCount = scriptMapper.selectCount(onlineScriptQw);

        QueryWrapper<DnSyncTask> onlineSyncQw = new QueryWrapper<>();
        onlineSyncQw.eq("schedule_status", Constants.SCHEDULE_ONLINE);
        long onlineSyncCount = syncTaskMapper.selectCount(onlineSyncQw);
        data.put("onlineCount", onlineScriptCount + onlineSyncCount);

        LocalDate today = LocalDate.now().minusDays(1);
        QueryWrapper<DnSchedulerRun> todayRunQw = new QueryWrapper<>();
        todayRunQw.eq("run_date", today).eq("run_type", Constants.RUN_TYPE_DAILY);
        data.put("todayExec", schedulerRunMapper.selectCount(todayRunQw));

        QueryWrapper<DnSchedulerRun> todaySuccessQw = new QueryWrapper<>();
        todaySuccessQw.eq("run_date", today).eq("run_type", Constants.RUN_TYPE_DAILY)
                      .eq("status", DnSchedulerRun.STATUS_SUCCESS);
        data.put("todaySuccess", schedulerRunMapper.selectCount(todaySuccessQw));

        QueryWrapper<DnSchedulerRun> todayFailQw = new QueryWrapper<>();
        todayFailQw.eq("run_date", today).eq("run_type", Constants.RUN_TYPE_DAILY)
                   .eq("status", DnSchedulerRun.STATUS_FAILED);
        data.put("todayFailed", schedulerRunMapper.selectCount(todayFailQw));

        return R.ok(data);
    }

    /**
     * 服务状态检测
     */
    @Operation(summary = "服务状态检测")
    @GetMapping("/services")
    public R<List<Map<String, Object>>> services() {
        List<Map<String, Object>> list = new ArrayList<>();
        list.add(checkService("Hadoop HDFS", "127.0.0.1", 9870, "分布式文件存储"));
        list.add(checkService("YARN", "127.0.0.1", 8088, "资源调度管理"));
        list.add(checkService("HiveServer2", "127.0.0.1", 10010, "Hive 查询服务"));
        list.add(checkService("Hive Metastore", "127.0.0.1", 9083, "Hive 元数据服务"));
        list.add(checkService("DolphinScheduler", "127.0.0.1", 12345, "任务调度平台"));
        list.add(checkService("MySQL", "127.0.0.1", 3306, "关系型数据库"));
        return R.ok(list);
    }

    private Map<String, Object> checkService(String name, String host, int port, String desc) {
        Map<String, Object> svc = new HashMap<>();
        svc.put("name", name);
        svc.put("port", port);
        svc.put("desc", desc);
        boolean alive = false;
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 1000);
            alive = true;
        } catch (Exception ignored) {}
        svc.put("alive", alive);
        return svc;
    }
}
