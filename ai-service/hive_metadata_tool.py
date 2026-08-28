"""
Hive 元数据查询工具（Function Calling / @tool）
================================================================
给 Agent 用的确定性元数据工具：查询 Hive 表名、字段名、类型、注释和分区字段。

注意：
  - 这里读取的是 Hive Metastore（datanote-mysql.hive_metastore），不是 MySQL 业务库。
  - 若数据地图维护了 dn_table_meta / dn_column_meta 的业务注释，会优先叠加使用。
"""
import os
import subprocess

import pymysql
from langchain_core.tools import tool


DB_CONF = dict(
    host=os.getenv("MYSQL_HOST", "127.0.0.1"),
    port=int(os.getenv("MYSQL_PORT", "3306")),
    user=os.getenv("MYSQL_USER", "root"),
    password=os.environ["MYSQL_PASSWORD"],
    charset="utf8mb4",
    cursorclass=pymysql.cursors.DictCursor,
)

HIVE_METASTORE_DB = os.getenv("HIVE_METASTORE_DB", "hive_metastore")
DATANOTE_DB = os.getenv("DATANOTE_DB", "datanote")
SYSTEM_DBS = {"default", "information_schema", "sys"}


def _query(sql: str, args=None):
    """内部：执行只读 MySQL 查询，用来读取 Hive Metastore。"""
    conn = pymysql.connect(**DB_CONF)
    try:
        with conn.cursor() as cur:
            cur.execute(sql, args or ())
            return cur.fetchall()
    finally:
        conn.close()


def _safe_name(name: str) -> str:
    """Hive 库/表名只允许字母、数字、下划线，避免拼接 metastore 查询时注入。"""
    name = (name or "").strip()
    if not name.replace("_", "").isalnum():
        raise ValueError(f"非法 Hive 名称：{name}")
    return name


def _readonly_sql(sql: str) -> str:
    s = (sql or "").strip().rstrip(";").strip()
    low = s.lower()
    readonly_prefix = ("select", "with", "show", "desc", "describe", "explain")
    forbidden = (
        "insert ", "update ", "delete ", "drop ", "truncate ", "alter ", "create ",
        "replace ", "grant ", "revoke ", "load data", "msck ", "analyze table",
    )
    if not low.startswith(readonly_prefix):
        raise ValueError("仅允许只读 HiveSQL（SELECT/WITH/SHOW/DESC/EXPLAIN）")
    if any(k in low for k in forbidden):
        raise ValueError("检测到写入或 DDL 关键词，拒绝执行")
    return s


@tool
def list_databases() -> str:
    """列出可用于数仓开发的 Hive 数据库（不含 default/information_schema/sys）。"""
    rows = _query(
        f"SELECT NAME AS name, `DESC` AS cmt "
        f"FROM {HIVE_METASTORE_DB}.DBS "
        f"ORDER BY NAME"
    )
    dbs = [r for r in rows if r["name"] not in SYSTEM_DBS]
    if not dbs:
        return "当前 Hive Metastore 中没有可用业务库"
    return "可用 Hive 数据库：\n" + "\n".join(
        f"- {r['name']}（{r['cmt'] or '无注释'}）" for r in dbs
    )


@tool
def list_tables(database: str) -> str:
    """列出指定 Hive 数据库下的表名和表注释。参数 database：Hive 库名。"""
    db = _safe_name(database)
    if db in SYSTEM_DBS:
        return f"Hive 数据库 {db} 不开放给业务查询"
    rows = _query(
        f"""
        SELECT
            d.NAME AS db_name,
            t.TBL_NAME AS table_name,
            t.TBL_TYPE AS table_type,
            COALESCE(NULLIF(tm.table_comment, ''), NULLIF(tp.PARAM_VALUE, ''), '') AS table_comment,
            COALESCE(cc.col_count, 0) AS col_count
        FROM {HIVE_METASTORE_DB}.DBS d
        JOIN {HIVE_METASTORE_DB}.TBLS t
          ON t.DB_ID = d.DB_ID
        LEFT JOIN (
            SELECT TBL_ID, PARAM_VALUE
            FROM {HIVE_METASTORE_DB}.TABLE_PARAMS
            WHERE PARAM_KEY = 'comment'
        ) tp
          ON tp.TBL_ID = t.TBL_ID
        LEFT JOIN {HIVE_METASTORE_DB}.SDS s
          ON s.SD_ID = t.SD_ID
        LEFT JOIN (
            SELECT CD_ID, COUNT(*) AS col_count
            FROM {HIVE_METASTORE_DB}.COLUMNS_V2
            GROUP BY CD_ID
        ) cc
          ON cc.CD_ID = s.CD_ID
        LEFT JOIN {DATANOTE_DB}.dn_table_meta tm
          ON tm.database_name = d.NAME
         AND tm.table_name = t.TBL_NAME
        WHERE d.NAME = %s
        ORDER BY t.TBL_NAME
        """,
        (db,),
    )
    if not rows:
        return f"Hive 数据库 {db} 下没有表，或库名不存在"
    lines = [
        f"- {r['table_name']}（{r['table_comment'] or '无注释'}；字段数 {r['col_count']}）"
        for r in rows
    ]
    return f"Hive 数据库 {db} 共 {len(rows)} 张表：\n" + "\n".join(lines)


@tool
def get_table_schema(database: str, table: str) -> str:
    """查询 Hive 表结构（字段名、类型、注释、分区字段）。生成 HiveSQL 前必须先查表结构。"""
    db = _safe_name(database)
    tbl = _safe_name(table)
    rows = _query(
        f"""
        SELECT
            d.NAME AS db_name,
            t.TBL_NAME AS table_name,
            COALESCE(NULLIF(tm.table_comment, ''), NULLIF(tp.PARAM_VALUE, ''), '') AS table_comment,
            c.COLUMN_NAME AS column_name,
            c.TYPE_NAME AS type_name,
            c.COMMENT AS hive_comment,
            cm.business_name AS business_name,
            cm.business_desc AS business_desc,
            c.INTEGER_IDX AS ordinal_position
        FROM {HIVE_METASTORE_DB}.DBS d
        JOIN {HIVE_METASTORE_DB}.TBLS t
          ON t.DB_ID = d.DB_ID
        LEFT JOIN {HIVE_METASTORE_DB}.TABLE_PARAMS tp
          ON tp.TBL_ID = t.TBL_ID
         AND tp.PARAM_KEY = 'comment'
        LEFT JOIN {HIVE_METASTORE_DB}.SDS s
          ON s.SD_ID = t.SD_ID
        LEFT JOIN {HIVE_METASTORE_DB}.COLUMNS_V2 c
          ON c.CD_ID = s.CD_ID
        LEFT JOIN {DATANOTE_DB}.dn_table_meta tm
          ON tm.database_name = d.NAME
         AND tm.table_name = t.TBL_NAME
        LEFT JOIN {DATANOTE_DB}.dn_column_meta cm
          ON cm.table_meta_id = tm.id
         AND cm.column_name = c.COLUMN_NAME
        WHERE d.NAME = %s
          AND t.TBL_NAME = %s
        ORDER BY c.INTEGER_IDX
        """,
        (db, tbl),
    )
    if not rows:
        return f"Hive 表 {db}.{tbl} 不存在或没有字段"

    table_comment = rows[0].get("table_comment") or "无注释"
    lines = [f"Hive 表结构 {db}.{tbl}（{table_comment}）："]
    for r in rows:
        col_name = r.get("column_name")
        if not col_name:
            continue
        comments = []
        if r.get("business_name"):
            comments.append(f"业务名：{r['business_name']}")
        if r.get("business_desc"):
            comments.append(f"业务口径：{r['business_desc']}")
        if r.get("hive_comment"):
            comments.append(str(r["hive_comment"]))
        comment_text = "；".join(comments)
        lines.append(f"  {col_name}  {r.get('type_name') or 'string'}  {comment_text}")

    pkeys = _query(
        f"""
        SELECT PKEY_NAME AS name, PKEY_TYPE AS type_name, PKEY_COMMENT AS comment
        FROM {HIVE_METASTORE_DB}.PARTITION_KEYS pk
        JOIN {HIVE_METASTORE_DB}.TBLS t
          ON t.TBL_ID = pk.TBL_ID
        JOIN {HIVE_METASTORE_DB}.DBS d
          ON d.DB_ID = t.DB_ID
        WHERE d.NAME = %s
          AND t.TBL_NAME = %s
        ORDER BY pk.INTEGER_IDX
        """,
        (db, tbl),
    )
    if pkeys:
        lines.append("分区字段：")
        for p in pkeys:
            lines.append(f"  {p['name']}  {p['type_name']}  {p['comment'] or ''}")
    return "\n".join(lines)


@tool
def search_tables(keyword: str) -> str:
    """按关键词搜索 Hive 表，匹配库名、表名、表注释、字段名和字段注释。"""
    kw = f"%{(keyword or '').strip()}%"
    rows = _query(
        f"""
        SELECT
            d.NAME AS db_name,
            t.TBL_NAME AS table_name,
            COALESCE(NULLIF(tm.table_comment, ''), NULLIF(tp.PARAM_VALUE, ''), '') AS table_comment,
            COALESCE(cc.col_count, 0) AS col_count
        FROM {HIVE_METASTORE_DB}.DBS d
        JOIN {HIVE_METASTORE_DB}.TBLS t
          ON t.DB_ID = d.DB_ID
        LEFT JOIN (
            SELECT TBL_ID, PARAM_VALUE
            FROM {HIVE_METASTORE_DB}.TABLE_PARAMS
            WHERE PARAM_KEY = 'comment'
        ) tp
          ON tp.TBL_ID = t.TBL_ID
        LEFT JOIN {HIVE_METASTORE_DB}.SDS s
          ON s.SD_ID = t.SD_ID
        LEFT JOIN (
            SELECT CD_ID, COUNT(*) AS col_count
            FROM {HIVE_METASTORE_DB}.COLUMNS_V2
            GROUP BY CD_ID
        ) cc
          ON cc.CD_ID = s.CD_ID
        LEFT JOIN {DATANOTE_DB}.dn_table_meta tm
          ON tm.database_name = d.NAME
         AND tm.table_name = t.TBL_NAME
        WHERE d.NAME NOT IN ('default', 'information_schema', 'sys')
          AND (
              d.NAME LIKE %s
           OR t.TBL_NAME LIKE %s
           OR tp.PARAM_VALUE LIKE %s
           OR tm.table_comment LIKE %s
           OR EXISTS (
                SELECT 1
                FROM {HIVE_METASTORE_DB}.COLUMNS_V2 c
                WHERE c.CD_ID = s.CD_ID
                  AND (c.COLUMN_NAME LIKE %s OR c.COMMENT LIKE %s)
           )
          )
        ORDER BY d.NAME, t.TBL_NAME
        LIMIT 50
        """,
        (kw, kw, kw, kw, kw, kw),
    )
    if not rows:
        return f"没有找到和「{keyword}」相关的 Hive 表"
    lines = [
        f"- {r['db_name']}.{r['table_name']}（{r['table_comment'] or '无注释'}；字段数 {r['col_count']}）"
        for r in rows
    ]
    return f"和「{keyword}」相关的 Hive 表：\n" + "\n".join(lines)


@tool
def execute_sql(sql: str) -> str:
    """执行一条只读 HiveSQL，用于核对或少量预览。生成 SQL 前应先查 get_table_schema。"""
    try:
        s = _readonly_sql(sql)
    except Exception as e:
        return f"拒绝执行：{e}"

    cmd = [
        "docker", "exec", "datanote-hiveserver2", "beeline",
        "-u", os.getenv("HIVE_BEELINE_URL", "jdbc:hive2://localhost:10000/default;auth=noSasl"),
        "--silent=true",
        "--showHeader=true",
        "--outputformat=tsv2",
        "-e", s,
    ]
    try:
        p = subprocess.run(cmd, capture_output=True, text=True, timeout=120)
    except Exception as e:
        return f"HiveSQL 执行失败：{e}"
    if p.returncode != 0:
        return f"HiveSQL 执行失败：{p.stderr.strip() or p.stdout.strip()}"
    out = (p.stdout or "").strip()
    if not out:
        return "HiveSQL 执行成功，结果为空"
    lines = out.splitlines()
    max_lines = 80
    if len(lines) > max_lines:
        return "\n".join(lines[:max_lines]) + f"\n...（结果过长，仅显示前 {max_lines} 行）"
    return out


HIVE_METADATA_TOOLS = [list_databases, list_tables, get_table_schema, search_tables]
HIVE_SQL_EXEC_TOOLS = [execute_sql]


if __name__ == "__main__":
    print(list_databases.invoke({}))
    print()
    print(search_tables.invoke({"keyword": "loan"}))
    print()
    print(get_table_schema.invoke({"database": "ods", "table": "ods_credit_center_credit_apply_df"}))
