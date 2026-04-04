package com.datanote.common;

/**
 * 全局常量定义
 */
public final class Constants {

    private Constants() {}

    // ========== 调度状态 ==========
    public static final String SCHEDULE_ONLINE = "online";
    public static final String SCHEDULE_OFFLINE = "offline";

    // ========== 任务类型 ==========
    public static final String TASK_TYPE_SCRIPT = "script";
    public static final String TASK_TYPE_SYNC_TASK = "syncTask";

    // ========== 运行类型 ==========
    public static final String RUN_TYPE_DAILY = "daily";
    public static final String RUN_TYPE_BACKFILL = "backfill";

    // ========== 脚本类型 ==========
    public static final String SCRIPT_TYPE_SQL = "sql";
    public static final String SCRIPT_TYPE_SHELL = "shell";

    // ========== 密码掩码 ==========
    public static final String PASSWORD_MASK = "***";

    // ========== 查询限制 ==========
    public static final int MAX_QUERY_ROWS = 500;

    // ========== 调度扫描间隔（毫秒） ==========
    public static final long TICK_INTERVAL_MS = 15000;

    // ========== 调度默认值 ==========
    public static final int DEFAULT_TIMEOUT_SECONDS = 0;
    public static final int DEFAULT_RETRY_TIMES = 0;
    public static final int DEFAULT_RETRY_INTERVAL = 60;

    // ========== 日志限制 ==========
    public static final int MAX_LOG_SIZE = 1024 * 1024; // 1MB

    // ========== 下游递归深度限制 ==========
    public static final int MAX_DOWNSTREAM_DEPTH = 100;
}
