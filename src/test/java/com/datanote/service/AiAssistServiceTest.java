package com.datanote.service;

import com.datanote.config.HiveConfig;
import com.datanote.mapper.DnSystemConfigMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiAssistServiceTest {

    @Mock
    private DnSystemConfigMapper systemConfigMapper;
    @Mock
    private HiveConfig hiveConfig;

    private AiAssistService service;

    @BeforeEach
    void setUp() {
        service = new AiAssistService(new ObjectMapper(), systemConfigMapper, hiveConfig);
    }

    @Test
    void ensureSafeSelectSql_appendsLimit_whenMissing() {
        String sql = service.ensureSafeSelectSql("select id, name from dwd_user");
        assertEquals("select id, name from dwd_user LIMIT 100", sql);
    }

    @Test
    void ensureSafeSelectSql_supportsSqlCodeBlock() {
        String sql = service.ensureSafeSelectSql("```sql\nselect * from ods_order\n```");
        assertEquals("select * from ods_order LIMIT 100", sql);
    }

    @Test
    void ensureSafeSelectSql_allowsTrailingSemicolon() {
        String sql = service.ensureSafeSelectSql("select * from ods_order;");
        assertEquals("select * from ods_order LIMIT 100", sql);
    }

    @Test
    void ensureSafeSelectSql_rejectsNonSelect() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.ensureSafeSelectSql("delete from ods_order"));
        assertTrue(ex.getMessage().contains("仅允许 SELECT"));
    }

    @Test
    void ensureSafeSelectSql_rejectsMultiStatement() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.ensureSafeSelectSql("select * from t1; select * from t2"));
        assertTrue(ex.getMessage().contains("仅允许单条"));
    }

    @Test
    void nl2sqlAgent_hiveUnavailable_returnsSuccess() {
        AiAssistService spy = org.mockito.Mockito.spy(service);
        when(hiveConfig.isHiveAvailable()).thenReturn(false);
        doReturn("```sql\nselect id from dwd_user\n```").when(spy).chat(anyString(), anyString());

        Map<String, Object> result = spy.nl2sqlAgent("查询用户ID", "table dwd_user(id string)");

        assertEquals("success", result.get("status"));
        assertEquals(1, result.get("attempts"));
        assertEquals("select id from dwd_user LIMIT 100", result.get("sql"));
    }

    @Test
    void nl2sqlAgent_repairOnce_thenSuccess() throws Exception {
        AiAssistService spy = org.mockito.Mockito.spy(service);
        when(hiveConfig.isHiveAvailable()).thenReturn(true);

        Connection conn = mock(Connection.class);
        Statement stmt = mock(Statement.class);
        ResultSet rs = mock(ResultSet.class);
        when(hiveConfig.getConnection()).thenReturn(conn);
        when(conn.createStatement()).thenReturn(stmt);
        when(stmt.executeQuery(anyString()))
                .thenThrow(new SQLException("bad column"))
                .thenReturn(rs);
        when(rs.next()).thenReturn(false);

        doReturn("```sql\nselect bad_col from dwd_user\n```")
                .doReturn("```sql\nselect id from dwd_user\n```")
                .when(spy).chat(anyString(), anyString());

        Map<String, Object> result = spy.nl2sqlAgent("查询用户ID", "table dwd_user(id string)");

        assertEquals("success", result.get("status"));
        assertEquals(2, result.get("attempts"));
        assertEquals("select id from dwd_user LIMIT 100", result.get("sql"));
    }

    @Test
    void nl2sqlAgent_nonSelect_returnsFailedImmediately() {
        AiAssistService spy = org.mockito.Mockito.spy(service);
        when(hiveConfig.isHiveAvailable()).thenReturn(false);
        doReturn("```sql\ndelete from dwd_user\n```").when(spy).chat(anyString(), anyString());

        Map<String, Object> result = spy.nl2sqlAgent("删数据", "");

        assertEquals("failed", result.get("status"));
        assertEquals(1, result.get("attempts"));
        assertNull(result.get("sql"));
        assertTrue(String.valueOf(result.get("error")).contains("仅允许 SELECT"));
    }
}
