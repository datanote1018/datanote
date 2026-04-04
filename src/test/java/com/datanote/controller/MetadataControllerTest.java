package com.datanote.controller;

import com.datanote.model.ColumnInfo;
import com.datanote.model.DnTableComment;
import com.datanote.model.R;
import com.datanote.service.DataMapService;
import com.datanote.service.MetadataService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MetadataControllerTest {

    @Mock
    private MetadataService metadataService;
    @Mock
    private DataMapService dataMapService;

    @InjectMocks
    private MetadataController controller;

    // ========== 基础元数据 ==========

    @Test
    void databases_returnsOk() throws Exception {
        when(metadataService.getDatabases()).thenReturn(Arrays.asList("datanote", "base_data"));
        R<List<String>> r = controller.databases();
        assertEquals(0, r.getCode());
        assertEquals(2, r.getData().size());
    }

    @Test
    void tables_returnsOk() throws Exception {
        when(metadataService.getTables("datanote")).thenReturn(Arrays.asList("dn_script", "dn_sync_task"));
        R<List<String>> r = controller.tables("datanote");
        assertEquals(0, r.getCode());
        assertEquals(2, r.getData().size());
    }

    @Test
    void columns_returnsOk() throws Exception {
        ColumnInfo col = new ColumnInfo();
        col.setName("id");
        col.setType("bigint");
        when(metadataService.getColumns("datanote", "dn_script")).thenReturn(Collections.singletonList(col));
        R<List<ColumnInfo>> r = controller.columns("datanote", "dn_script");
        assertEquals(0, r.getCode());
        assertEquals("id", r.getData().get(0).getName());
    }

    // ========== 搜索 ==========

    @Test
    void search_emptyKeyword_returnsEmptyList() {
        R<List<Map<String, Object>>> r = controller.search("");
        assertEquals(0, r.getCode());
        assertTrue(r.getData().isEmpty());
    }

    @Test
    void search_nullKeyword_returnsEmptyList() {
        R<List<Map<String, Object>>> r = controller.search(null);
        assertEquals(0, r.getCode());
    }

    @Test
    void search_withKeyword_returnsResults() throws Exception {
        Map<String, Object> row = new HashMap<>();
        row.put("TABLE_NAME", "dn_script");
        when(dataMapService.searchTables("dn")).thenReturn(Collections.singletonList(row));
        R<List<Map<String, Object>>> r = controller.search("dn");
        assertEquals(0, r.getCode());
        assertEquals("dn_script", r.getData().get(0).get("TABLE_NAME"));
    }

    // ========== 收藏 ==========

    @Test
    void toggleFavorite_missingParams_returnsFail() {
        R<Map<String, Object>> r = controller.toggleFavorite(new HashMap<String, String>());
        assertEquals(-1, r.getCode());
    }

    @Test
    void toggleFavorite_validParams_returnsOk() {
        when(dataMapService.toggleFavorite("datanote", "dn_script")).thenReturn(true);
        Map<String, String> body = new HashMap<>();
        body.put("databaseName", "datanote");
        body.put("tableName", "dn_script");
        R<Map<String, Object>> r = controller.toggleFavorite(body);
        assertEquals(0, r.getCode());
        assertEquals(true, r.getData().get("favorited"));
    }

    // ========== 评论 ==========

    @Test
    void addComment_missingDb_returnsFail() {
        Map<String, String> body = new HashMap<>();
        body.put("content", "test");
        R<DnTableComment> r = controller.addComment(body);
        assertEquals(-1, r.getCode());
    }

    @Test
    void addComment_missingContent_returnsFail() {
        Map<String, String> body = new HashMap<>();
        body.put("db", "datanote");
        body.put("table", "dn_script");
        R<DnTableComment> r = controller.addComment(body);
        assertEquals(-1, r.getCode());
    }

    @Test
    void addComment_valid_returnsOk() {
        DnTableComment comment = new DnTableComment();
        comment.setId(1L);
        comment.setContent("good");
        when(dataMapService.addComment("datanote", "dn_script", "good")).thenReturn(comment);
        Map<String, String> body = new HashMap<>();
        body.put("db", "datanote");
        body.put("table", "dn_script");
        body.put("content", "good");
        R<DnTableComment> r = controller.addComment(body);
        assertEquals(0, r.getCode());
        assertEquals("good", r.getData().getContent());
    }

    // ========== 安全 ==========

    @Test
    void preview_invalidDbName_returnsFail() {
        R<Map<String, Object>> r = controller.preview("datanote;DROP", "dn_script");
        assertEquals(-1, r.getCode());
    }

    @Test
    void preview_invalidTableName_returnsFail() {
        R<Map<String, Object>> r = controller.preview("datanote", "dn script");
        assertEquals(-1, r.getCode());
    }

    @Test
    void ddl_invalidName_returnsFail() {
        R<Map<String, String>> r = controller.ddl("db'--", "t");
        assertEquals(-1, r.getCode());
    }

    @Test
    void tableDetail_invalidName_returnsFail() {
        R<Map<String, Object>> r = controller.tableDetail("db;", "t");
        assertEquals(-1, r.getCode());
    }

    // ========== 正常流程 ==========

    @Test
    void preview_validParams_returnsOk() throws Exception {
        Map<String, Object> result = new HashMap<>();
        result.put("rowCount", 5);
        when(dataMapService.preview("datanote", "dn_script")).thenReturn(result);
        R<Map<String, Object>> r = controller.preview("datanote", "dn_script");
        assertEquals(0, r.getCode());
        assertEquals(5, r.getData().get("rowCount"));
    }

    @Test
    void popular_returnsOk() throws Exception {
        when(dataMapService.getPopularTables()).thenReturn(Collections.emptyList());
        R<List<Map<String, Object>>> r = controller.popular();
        assertEquals(0, r.getCode());
    }

    @Test
    void searchHistory_returnsOk() {
        when(dataMapService.getSearchHistory()).thenReturn(Collections.emptyList());
        R r = controller.searchHistory();
        assertEquals(0, r.getCode());
    }

    @Test
    void favorites_returnsOk() {
        when(dataMapService.getFavorites()).thenReturn(Collections.emptyList());
        R r = controller.favorites();
        assertEquals(0, r.getCode());
    }
}
