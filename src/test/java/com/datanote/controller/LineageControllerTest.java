package com.datanote.controller;

import com.datanote.model.DnScript;
import com.datanote.model.DnTaskDependency;
import com.datanote.model.R;
import com.datanote.service.DolphinService;
import com.datanote.service.ScriptService;
import com.datanote.service.TaskDependencyService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LineageControllerTest {

    @Mock
    private DolphinService dolphinService;
    @Mock
    private ScriptService scriptService;
    @Mock
    private TaskDependencyService taskDependencyService;

    @InjectMocks
    private LineageController controller;

    @Test
    void refreshDeps_returnsOk() {
        when(taskDependencyService.refreshAllDependencies()).thenReturn(5);
        R<Map<String, Object>> r = controller.refreshDeps();
        assertEquals(0, r.getCode());
        assertEquals(5, r.getData().get("dependencyCount"));
    }

    @Test
    void searchTasks_returnsOk() {
        Map<String, Object> task = new HashMap<>();
        task.put("taskName", "test");
        when(taskDependencyService.searchOnlineTasks("test")).thenReturn(Collections.singletonList(task));
        R<List<Map<String, Object>>> r = controller.searchTasks("test");
        assertEquals(0, r.getCode());
        assertEquals(1, r.getData().size());
    }

    @Test
    void addDep_success() {
        when(taskDependencyService.addManualDependency(eq(2L), eq("script"), eq(1L), eq("syncTask"), anyString()))
                .thenReturn(true);
        Map<String, Object> body = new HashMap<>();
        body.put("taskId", 2L);
        body.put("taskType", "script");
        body.put("upstreamTaskId", 1L);
        body.put("upstreamTaskType", "syncTask");
        body.put("depTable", "test");
        R<String> r = controller.addDependency(body);
        assertEquals(0, r.getCode());
    }

    @Test
    void addDep_duplicate_returnsFail() {
        when(taskDependencyService.addManualDependency(anyLong(), anyString(), anyLong(), anyString(), any()))
                .thenReturn(false);
        Map<String, Object> body = new HashMap<>();
        body.put("taskId", 2L);
        body.put("taskType", "script");
        body.put("upstreamTaskId", 1L);
        body.put("upstreamTaskType", "syncTask");
        R<String> r = controller.addDependency(body);
        assertEquals(-1, r.getCode());
    }

    @Test
    void listDeps_returnsOk() {
        DnTaskDependency dep = new DnTaskDependency();
        dep.setTaskId(2L);
        when(taskDependencyService.listDependencies(2L, "script")).thenReturn(Collections.singletonList(dep));
        R<List<DnTaskDependency>> r = controller.listDeps(2L, "script");
        assertEquals(0, r.getCode());
        assertEquals(1, r.getData().size());
    }

    @Test
    void getDownstreamTree_returnsOk() {
        when(taskDependencyService.getDownstreamTree(2L, "script")).thenReturn(Collections.emptyList());
        R<List<Map<String, Object>>> r = controller.getDownstreamTree(2L, "script");
        assertEquals(0, r.getCode());
    }

    @Test
    void getLineage_scriptNotFound_throwsException() {
        when(scriptService.getById(999L)).thenReturn(null);
        assertThrows(com.datanote.exception.ResourceNotFoundException.class,
                new org.junit.jupiter.api.function.Executable() {
                    public void execute() { controller.getLineage(999L); }
                });
    }

    @Test
    void getLineage_noWorkflow_returnsEmptyJson() {
        DnScript script = new DnScript();
        script.setId(1L);
        script.setDsWorkflowCode(0L);
        when(scriptService.getById(1L)).thenReturn(script);
        R r = controller.getLineage(1L);
        assertEquals(0, r.getCode());
    }
}
