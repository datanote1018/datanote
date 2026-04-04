package com.datanote.controller;

import com.datanote.model.*;
import com.datanote.service.ScriptService;
import com.datanote.model.dto.MoveScriptRequest;
import com.datanote.model.dto.RenameFolderRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ScriptControllerTest {

    @Mock
    private ScriptService scriptService;

    @InjectMocks
    private ScriptController controller;

    @Test
    void tree_returnsOk() {
        Map<String, Object> node = new HashMap<>();
        node.put("id", 1L);
        node.put("name", "DWD 层");
        when(scriptService.getTree()).thenReturn(Collections.singletonList(node));
        R<List<Map<String, Object>>> r = controller.tree();
        assertEquals(0, r.getCode());
        assertEquals("DWD 层", r.getData().get(0).get("name"));
    }

    @Test
    void getById_returnsScript() {
        DnScript script = new DnScript();
        script.setId(2L);
        script.setScriptName("test");
        when(scriptService.getById(2L)).thenReturn(script);
        R<DnScript> r = controller.getById(2L);
        assertEquals(0, r.getCode());
        assertEquals("test", r.getData().getScriptName());
    }

    @Test
    void save_returnsOk() {
        DnScript saved = new DnScript();
        saved.setId(10L);
        when(scriptService.save(any(DnScript.class))).thenReturn(saved);
        R<DnScript> r = controller.save(new DnScript());
        assertEquals(0, r.getCode());
        assertEquals(10L, r.getData().getId());
    }

    @Test
    void listVersions_returnsOk() {
        DnScriptVersion v = new DnScriptVersion();
        v.setVersion(1);
        when(scriptService.listVersions(2L)).thenReturn(Collections.singletonList(v));
        R<List<DnScriptVersion>> r = controller.listVersions(2L);
        assertEquals(0, r.getCode());
        assertEquals(1, r.getData().size());
    }

    @Test
    void deleteScript_callsService() {
        R<String> r = controller.delete(999L);
        assertEquals(0, r.getCode());
        verify(scriptService).delete(999L);
    }

    @Test
    void createFolder_returnsOk() {
        DnScriptFolder folder = new DnScriptFolder();
        folder.setId(10L);
        folder.setFolderName("新文件夹");
        when(scriptService.createFolder(any(DnScriptFolder.class))).thenReturn(folder);
        R<DnScriptFolder> r = controller.createFolder(new DnScriptFolder());
        assertEquals(0, r.getCode());
    }

    @Test
    void saveSyncTask_returnsOk() {
        DnSyncTask task = new DnSyncTask();
        task.setId(1L);
        task.setTaskName("ods_test_df");
        when(scriptService.saveSyncTask(any(DnSyncTask.class))).thenReturn(task);
        R<DnSyncTask> r = controller.saveSyncTask(new DnSyncTask());
        assertEquals(0, r.getCode());
        assertEquals("ods_test_df", r.getData().getTaskName());
    }

    @Test
    void deleteSyncTask_callsService() {
        R<String> r = controller.deleteSyncTask(1L);
        assertEquals(0, r.getCode());
        verify(scriptService).deleteSyncTask(1L);
    }

    @Test
    void allWithContent_returnsOk() {
        when(scriptService.allWithContent()).thenReturn(Collections.emptyList());
        R<List<Map<String, Object>>> r = controller.allWithContent();
        assertEquals(0, r.getCode());
    }
}
