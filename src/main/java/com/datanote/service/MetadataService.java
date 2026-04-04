package com.datanote.service;

import com.datanote.model.ColumnInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * 元数据查询服务 — 支持默认数据源和自定义外部连接
 */
@Service
@RequiredArgsConstructor
public class MetadataService {

    private final DataSource dataSource;

    private static final String SQL_DATABASES =
            "SELECT SCHEMA_NAME FROM information_schema.SCHEMATA "
            + "WHERE SCHEMA_NAME NOT IN ('information_schema','performance_schema','mysql','sys') "
            + "ORDER BY SCHEMA_NAME";

    private static final String SQL_TABLES =
            "SELECT TABLE_NAME FROM information_schema.TABLES "
            + "WHERE TABLE_SCHEMA = ? AND TABLE_TYPE = 'BASE TABLE' "
            + "ORDER BY TABLE_NAME";

    private static final String SQL_COLUMNS =
            "SELECT COLUMN_NAME, COLUMN_TYPE, COLUMN_COMMENT, COLUMN_KEY, IS_NULLABLE, EXTRA "
            + "FROM information_schema.COLUMNS "
            + "WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ? "
            + "ORDER BY ORDINAL_POSITION";

    /**
     * 获取默认数据源的所有数据库（排除系统库）
     */
    public List<String> getDatabases() throws SQLException {
        try (Connection conn = dataSource.getConnection()) {
            return queryDatabases(conn);
        }
    }

    /**
     * 获取外部连接的所有数据库
     */
    public List<String> getDatabasesByConnection(String host, int port, String username, String password) throws SQLException {
        try (Connection conn = getExternalConnection(host, port, username, password)) {
            return queryDatabases(conn);
        }
    }

    /**
     * 获取默认数据源指定库的所有表
     */
    public List<String> getTables(String db) throws SQLException {
        try (Connection conn = dataSource.getConnection()) {
            return queryTables(conn, db);
        }
    }

    /**
     * 获取外部连接指定库的所有表
     */
    public List<String> getTablesByConnection(String host, int port, String username, String password, String db) throws SQLException {
        try (Connection conn = getExternalConnection(host, port, username, password)) {
            return queryTables(conn, db);
        }
    }

    /**
     * 获取默认数据源指定表的所有字段信息
     */
    public List<ColumnInfo> getColumns(String db, String table) throws SQLException {
        try (Connection conn = dataSource.getConnection()) {
            return queryColumns(conn, db, table);
        }
    }

    /**
     * 获取外部连接指定表的所有字段信息
     */
    public List<ColumnInfo> getColumnsByConnection(String host, int port, String username, String password, String db, String table) throws SQLException {
        try (Connection conn = getExternalConnection(host, port, username, password)) {
            return queryColumns(conn, db, table);
        }
    }

    // ========== 核心查询逻辑（消除重复） ==========

    private List<String> queryDatabases(Connection conn) throws SQLException {
        List<String> list = new ArrayList<>();
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(SQL_DATABASES)) {
            while (rs.next()) {
                list.add(rs.getString(1));
            }
        }
        return list;
    }

    private List<String> queryTables(Connection conn, String db) throws SQLException {
        List<String> list = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(SQL_TABLES)) {
            ps.setString(1, db);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(rs.getString(1));
                }
            }
        }
        return list;
    }

    private List<ColumnInfo> queryColumns(Connection conn, String db, String table) throws SQLException {
        List<ColumnInfo> list = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(SQL_COLUMNS)) {
            ps.setString(1, db);
            ps.setString(2, table);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ColumnInfo col = new ColumnInfo();
                    col.setName(rs.getString("COLUMN_NAME"));
                    col.setType(rs.getString("COLUMN_TYPE"));
                    col.setComment(rs.getString("COLUMN_COMMENT"));
                    col.setKey(rs.getString("COLUMN_KEY"));
                    col.setNullable(rs.getString("IS_NULLABLE"));
                    col.setExtra(rs.getString("EXTRA"));
                    col.setHiveType("string");
                    list.add(col);
                }
            }
        }
        return list;
    }

    private Connection getExternalConnection(String host, int port, String username, String password) throws SQLException {
        String url = "jdbc:mysql://" + host + ":" + port
                + "/?useUnicode=true&characterEncoding=UTF-8&useSSL=false&allowPublicKeyRetrieval=true";
        return DriverManager.getConnection(url, username, password);
    }
}
