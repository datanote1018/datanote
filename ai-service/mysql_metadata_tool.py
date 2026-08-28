"""
MySQL 元数据查询工具（Function Calling / @tool）
================================================================
给 Agent 用的"确定性查询"工具：知道要查哪个库/哪张表，直接精确查结构。
（与 RAG 的区别：RAG 是模糊/语义检索业务知识；这里是精确查表结构。）

依赖：
  pip install pymysql langchain-core -i https://pypi.tuna.tsinghua.edu.cn/simple

连接：本机 datanote-mysql —— localhost:3306, root/root
"""
import os
import pymysql
from langchain_core.tools import tool

# ---------- 连接配置 ----------
DB_CONF = dict(
    host=os.getenv("MYSQL_HOST", "127.0.0.1"),
    port=int(os.getenv("MYSQL_PORT", "3306")),
    user=os.getenv("MYSQL_USER", "root"),
    password=os.environ["MYSQL_PASSWORD"],
    charset="utf8mb4",
    cursorclass=pymysql.cursors.DictCursor,
)

# 系统库不对业务开放
SYSTEM_DBS = {"mysql", "information_schema", "performance_schema", "sys"}


def _query(sql: str, args=None):
    """内部：执行只读查询。"""
    conn = pymysql.connect(**DB_CONF)
    try:
        with conn.cursor() as cur:
            cur.execute(sql, args or ())
            return cur.fetchall()
    finally:
        conn.close()


@tool
def list_databases() -> str:
    """列出所有可用的业务数据库（不含系统库）。当不确定数据在哪个库时调用。"""
    rows = _query("SHOW DATABASES")
    dbs = [r["Database"] for r in rows if r["Database"] not in SYSTEM_DBS]
    return "可用数据库：" + "、".join(dbs)


@tool
def list_tables(database: str) -> str:
    """列出指定数据库下的所有表名。参数 database：库名。用于查看某个库里有哪些表。"""
    if database in SYSTEM_DBS:
        return f"数据库 {database} 不开放查询"
    rows = _query(
        "SELECT table_name AS name, table_comment AS cmt FROM information_schema.tables "
        "WHERE table_schema=%s ORDER BY table_name",
        (database,),
    )
    if not rows:
        return f"数据库 {database} 下没有表，或库名不存在"
    lines = [f"- {r['name']}（{r['cmt'] or '无注释'}）" for r in rows]
    return f"数据库 {database} 共 {len(rows)} 张表：\n" + "\n".join(lines)


@tool
def get_table_schema(database: str, table: str) -> str:
    """查询指定表的结构（字段名、类型、注释、是否主键）。
    参数 database：库名；table：表名。生成 SQL 前必须先查表结构。"""
    if database in SYSTEM_DBS:
        return f"数据库 {database} 不开放查询"
    rows = _query(
        "SELECT column_name AS name, column_type AS ctype, column_key AS ckey, "
        "column_comment AS cmt FROM information_schema.columns "
        "WHERE table_schema=%s AND table_name=%s ORDER BY ordinal_position",
        (database, table),
    )
    if not rows:
        return f"表 {database}.{table} 不存在或没有字段"
    lines = [f"表结构 {database}.{table}："]
    for r in rows:
        pk = " [主键]" if r["ckey"] == "PRI" else ""
        lines.append(f"  {r['name']}  {r['ctype']}  {r['cmt'] or ''}{pk}")
    return "\n".join(lines)


@tool
def search_tables(keyword: str) -> str:
    """按关键词模糊搜索表（匹配表名或表注释），跨所有业务库。
    参数 keyword：关键词。当不知道确切表名、只知道业务含义时用它先定位表。"""
    placeholders = ",".join(["%s"] * len(SYSTEM_DBS))
    rows = _query(
        f"SELECT table_schema AS db, table_name AS name, table_comment AS cmt "
        f"FROM information_schema.tables "
        f"WHERE table_schema NOT IN ({placeholders}) "
        f"AND (table_name LIKE %s OR table_comment LIKE %s) LIMIT 30",
        (*SYSTEM_DBS, f"%{keyword}%", f"%{keyword}%"),
    )
    if not rows:
        return f"没有找到和「{keyword}」相关的表"
    lines = [f"- {r['db']}.{r['name']}（{r['cmt'] or '无注释'}）" for r in rows]
    return f"和「{keyword}」相关的表：\n" + "\n".join(lines)


# 允许执行的只读语句前缀（防止 Agent 误写数据）
_READONLY_PREFIX = ("select", "with", "show", "desc", "describe", "explain")
# 明确禁止的危险关键词（即便夹在 SELECT 里也拦）
_FORBIDDEN = ("insert ", "update ", "delete ", "drop ", "truncate ", "alter ",
              "create ", "replace ", "grant ", "revoke ", "into outfile", "load data")
_MAX_ROWS = 200      # 结果最多返回行数，避免把大表灌进上下文


@tool
def execute_sql(sql: str) -> str:
    """执行一条【只读】SQL（SELECT/WITH/SHOW）并返回结果，用于真正出数或核对校验。
    参数 sql：完整的 SQL 语句。仅允许只读查询，写操作会被拒绝；结果自动截断到 200 行。
    调用前应先用 get_table_schema 确认字段真实存在。"""
    s = (sql or "").strip().rstrip(";").strip()
    low = s.lower()
    if not low.startswith(_READONLY_PREFIX):
        return "拒绝执行：仅允许只读查询（SELECT/WITH/SHOW/DESC/EXPLAIN）"
    if any(k in low for k in _FORBIDDEN):
        return "拒绝执行：检测到写/DDL 关键词，只读查询不允许"
    try:
        rows = _query(s)
    except Exception as e:
        return f"SQL 执行失败：{e}"
    if not rows:
        return "查询成功，结果为空（0 行）"
    total = len(rows)
    rows = rows[:_MAX_ROWS]
    cols = list(rows[0].keys())
    lines = [" | ".join(cols)]
    for r in rows:
        lines.append(" | ".join("" if r[c] is None else str(r[c]) for c in cols))
    head = f"共 {total} 行" + (f"（仅显示前 {_MAX_ROWS} 行）" if total > _MAX_ROWS else "") + "：\n"
    return head + "\n".join(lines)


# 供 Agent 绑定的工具列表
MYSQL_TOOLS = [list_databases, list_tables, get_table_schema, search_tables]
# 出数/核对 Agent 额外用的「执行」工具
SQL_EXEC_TOOLS = [execute_sql]


# ---------- 自测 ----------
if __name__ == "__main__":
    print(list_databases.invoke({}))
    print()
    print(search_tables.invoke({"keyword": "loan"}))
    print()
    # 举例：查 loan_center 有哪些表（按你实际库改）
    print(list_tables.invoke({"database": "loan_center"}))
