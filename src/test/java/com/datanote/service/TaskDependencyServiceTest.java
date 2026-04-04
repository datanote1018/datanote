package com.datanote.service;

import com.datanote.mapper.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class TaskDependencyServiceTest {

    @Mock
    private DnScriptMapper scriptMapper;
    @Mock
    private DnSyncTaskMapper syncTaskMapper;
    @Mock
    private DnScriptFolderMapper folderMapper;
    @Mock
    private DnTaskDependencyMapper depMapper;
    @Mock
    private DnSchedulerRunMapper runMapper;

    private TaskDependencyService service;

    @BeforeEach
    void setUp() {
        service = new TaskDependencyService(scriptMapper, syncTaskMapper, folderMapper, depMapper, runMapper);
    }

    // ==================== parseSQLTables ====================

    @Test
    void parseSQLTables_simpleSelect() {
        Set<String> tables = service.parseSQLTables("SELECT * FROM user_info WHERE id = 1");
        assertTrue(tables.contains("user_info"));
    }

    @Test
    void parseSQLTables_withJoin() {
        String sql = "SELECT a.*, b.name FROM orders a JOIN customers b ON a.cid = b.id";
        Set<String> tables = service.parseSQLTables(sql);
        assertTrue(tables.contains("orders"));
        assertTrue(tables.contains("customers"));
    }

    @Test
    void parseSQLTables_multipleFromAndJoin() {
        String sql = "SELECT * FROM table_a a "
                + "LEFT JOIN table_b b ON a.id = b.aid "
                + "INNER JOIN table_c c ON b.id = c.bid";
        Set<String> tables = service.parseSQLTables(sql);
        assertTrue(tables.contains("table_a"));
        assertTrue(tables.contains("table_b"));
        assertTrue(tables.contains("table_c"));
    }

    @Test
    void parseSQLTables_withDatabasePrefix() {
        String sql = "SELECT * FROM mydb.my_table";
        Set<String> tables = service.parseSQLTables(sql);
        assertTrue(tables.contains("my_table"));
    }

    @Test
    void parseSQLTables_withBackticks() {
        String sql = "SELECT * FROM `ods`.`user_order`";
        Set<String> tables = service.parseSQLTables(sql);
        assertTrue(tables.contains("user_order"));
    }

    @Test
    void parseSQLTables_filtersSQLKeywords() {
        // "select" / "where" 等不应被当作表名
        String sql = "SELECT * FROM select_data WHERE id > 0";
        Set<String> tables = service.parseSQLTables(sql);
        assertTrue(tables.contains("select_data"));
        // "select" 本身如果出现在 FROM 后面会被过滤
    }

    @Test
    void parseSQLTables_ignoresComments() {
        String sql = "-- this is a comment\n"
                + "SELECT * FROM real_table\n"
                + "/* block comment FROM fake_table */";
        Set<String> tables = service.parseSQLTables(sql);
        assertTrue(tables.contains("real_table"));
        assertFalse(tables.contains("fake_table"));
    }

    @Test
    void parseSQLTables_nullInput_returnsEmpty() {
        Set<String> tables = service.parseSQLTables(null);
        assertTrue(tables.isEmpty());
    }

    @Test
    void parseSQLTables_emptyInput_returnsEmpty() {
        Set<String> tables = service.parseSQLTables("");
        assertTrue(tables.isEmpty());
    }

    @Test
    void parseSQLTables_caseInsensitive() {
        String sql = "select * from My_Table JOIN Another_Table on 1=1";
        Set<String> tables = service.parseSQLTables(sql);
        assertTrue(tables.contains("My_Table"));
        assertTrue(tables.contains("Another_Table"));
    }

    // ==================== hasCycle ====================

    @Test
    void hasCycle_noCycle_returnsFalse() throws Exception {
        // A -> B -> C (线性，无环)
        Map<String, Set<String>> graph = new HashMap<>();
        graph.put("A", new HashSet<>(Collections.singletonList("B")));
        graph.put("B", new HashSet<>(Collections.singletonList("C")));
        graph.put("C", new HashSet<>());

        assertFalse(invokeHasCycle(graph));
    }

    @Test
    void hasCycle_simpleCycle_returnsTrue() throws Exception {
        // A -> B -> C -> A
        Map<String, Set<String>> graph = new HashMap<>();
        graph.put("A", new HashSet<>(Collections.singletonList("B")));
        graph.put("B", new HashSet<>(Collections.singletonList("C")));
        graph.put("C", new HashSet<>(Collections.singletonList("A")));

        assertTrue(invokeHasCycle(graph));
    }

    @Test
    void hasCycle_selfLoop_returnsTrue() throws Exception {
        // A -> A
        Map<String, Set<String>> graph = new HashMap<>();
        graph.put("A", new HashSet<>(Collections.singletonList("A")));

        assertTrue(invokeHasCycle(graph));
    }

    @Test
    void hasCycle_emptyGraph_returnsFalse() throws Exception {
        Map<String, Set<String>> graph = new HashMap<>();
        assertFalse(invokeHasCycle(graph));
    }

    @Test
    void hasCycle_disconnectedComponents_noCycle() throws Exception {
        // A -> B, C -> D (两个独立链，无环)
        Map<String, Set<String>> graph = new HashMap<>();
        graph.put("A", new HashSet<>(Collections.singletonList("B")));
        graph.put("B", new HashSet<>());
        graph.put("C", new HashSet<>(Collections.singletonList("D")));
        graph.put("D", new HashSet<>());

        assertFalse(invokeHasCycle(graph));
    }

    @Test
    void hasCycle_complexGraphWithCycle() throws Exception {
        // A -> B, A -> C, B -> D, C -> D, D -> A
        Map<String, Set<String>> graph = new HashMap<>();
        graph.put("A", new HashSet<>(Arrays.asList("B", "C")));
        graph.put("B", new HashSet<>(Collections.singletonList("D")));
        graph.put("C", new HashSet<>(Collections.singletonList("D")));
        graph.put("D", new HashSet<>(Collections.singletonList("A")));

        assertTrue(invokeHasCycle(graph));
    }

    @Test
    void hasCycle_diamondShape_noCycle() throws Exception {
        // A -> B, A -> C, B -> D, C -> D (菱形，无环)
        Map<String, Set<String>> graph = new HashMap<>();
        graph.put("A", new HashSet<>(Arrays.asList("B", "C")));
        graph.put("B", new HashSet<>(Collections.singletonList("D")));
        graph.put("C", new HashSet<>(Collections.singletonList("D")));
        graph.put("D", new HashSet<>());

        assertFalse(invokeHasCycle(graph));
    }

    /**
     * 通过反射调用 private hasCycle 方法
     */
    @SuppressWarnings("unchecked")
    private boolean invokeHasCycle(Map<String, Set<String>> graph) throws Exception {
        Method method = TaskDependencyService.class.getDeclaredMethod("hasCycle", Map.class);
        method.setAccessible(true);
        return (boolean) method.invoke(service, graph);
    }
}
