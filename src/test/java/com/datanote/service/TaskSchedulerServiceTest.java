package com.datanote.service;

import com.datanote.mapper.*;
import com.datanote.model.DnScript;
import com.datanote.model.DnSyncTask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskSchedulerServiceTest {

    @Mock
    private DnScriptMapper scriptMapper;
    @Mock
    private DnSyncTaskMapper syncTaskMapper;
    @Mock
    private DnScriptFolderMapper folderMapper;
    @Mock
    private DnSchedulerRunMapper runMapper;
    @Mock
    private TaskExecutionService taskExecutionService;
    @Mock
    private TaskDependencyService taskDependencyService;

    private TaskSchedulerService service;

    @BeforeEach
    void setUp() {
        service = new TaskSchedulerService(
                scriptMapper, syncTaskMapper, folderMapper, runMapper,
                taskDependencyService
        );
        service.setTaskExecutionService(taskExecutionService);
    }

    // ==================== isScheduleTimeReady ====================

    @Test
    void isScheduleTimeReady_nullCron_returnsTrue() throws Exception {
        DnScript script = new DnScript();
        script.setId(1L);
        script.setScheduleCron(null);
        when(scriptMapper.selectById(1L)).thenReturn(script);

        boolean result = invokeIsScheduleTimeReady(1L, "script");
        assertTrue(result, "cron 为 null 应直接放行");
    }

    @Test
    void isScheduleTimeReady_emptyCron_returnsTrue() throws Exception {
        DnScript script = new DnScript();
        script.setId(1L);
        script.setScheduleCron("  ");
        when(scriptMapper.selectById(1L)).thenReturn(script);

        boolean result = invokeIsScheduleTimeReady(1L, "script");
        assertTrue(result, "cron 为空白应直接放行");
    }

    @Test
    void isScheduleTimeReady_invalidCron_returnsTrue() throws Exception {
        DnScript script = new DnScript();
        script.setId(1L);
        script.setScheduleCron("not-a-valid-cron");
        when(scriptMapper.selectById(1L)).thenReturn(script);

        boolean result = invokeIsScheduleTimeReady(1L, "script");
        assertTrue(result, "无效 cron 应默认放行");
    }

    @Test
    void isScheduleTimeReady_pastCron_returnsTrue() throws Exception {
        // 设定一个已过去的时间（凌晨 00:01）
        DnScript script = new DnScript();
        script.setId(1L);
        script.setScheduleCron("0 1 0 * * *"); // 每天 00:01
        when(scriptMapper.selectById(1L)).thenReturn(script);

        boolean result = invokeIsScheduleTimeReady(1L, "script");
        // 如果当前时间在 00:01 之后则为 true
        if (LocalTime.now().isAfter(LocalTime.of(0, 1))) {
            assertTrue(result);
        }
        // 不做 else 断言，避免在凌晨跑测试时误判
    }

    @Test
    void isScheduleTimeReady_futureCron_returnsFalse() throws Exception {
        // 设定一个远在未来的时间（23:59）
        DnScript script = new DnScript();
        script.setId(1L);
        script.setScheduleCron("0 59 23 * * *"); // 每天 23:59
        when(scriptMapper.selectById(1L)).thenReturn(script);

        boolean result = invokeIsScheduleTimeReady(1L, "script");
        // 如果当前时间在 23:59 之前则为 false
        if (LocalTime.now().isBefore(LocalTime.of(23, 59))) {
            assertFalse(result);
        }
    }

    @Test
    void isScheduleTimeReady_syncTask_nullCron_returnsTrue() throws Exception {
        DnSyncTask task = new DnSyncTask();
        task.setId(1L);
        task.setScheduleCron(null);
        when(syncTaskMapper.selectById(1L)).thenReturn(task);

        boolean result = invokeIsScheduleTimeReady(1L, "syncTask");
        assertTrue(result);
    }

    @Test
    void isScheduleTimeReady_taskNotFound_returnsTrue() throws Exception {
        when(scriptMapper.selectById(anyLong())).thenReturn(null);

        boolean result = invokeIsScheduleTimeReady(999L, "script");
        assertTrue(result, "任务不存在时 cron 为 null，应默认放行");
    }

    /**
     * 通过反射调用 private isScheduleTimeReady 方法
     */
    private boolean invokeIsScheduleTimeReady(Long taskId, String taskType) throws Exception {
        Method method = TaskSchedulerService.class.getDeclaredMethod("isScheduleTimeReady", Long.class, String.class);
        method.setAccessible(true);
        return (boolean) method.invoke(service, taskId, taskType);
    }
}
