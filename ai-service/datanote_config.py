"""
读取 datanote 里配置的 AI 参数（API Key / Base URL / Model）
================================================================
配置来源：
  · API Key —— 只从本地 .env 读（QWEN_API_KEY / DASHSCOPE_API_KEY）。
    不再从数据库解密：密钥留在本机文件里，既不进数据库也不进仓库，
    也就不需要维护那把 AES crypto key。
  · Base URL / Model —— 优先用 datanote「系统管理 → AI 配置」里的设置
    （这两项不是机密，放数据库便于在页面上随时切换），读不到则用默认值。

首次部署：复制 env.example 为 .env，填入自己的 API Key。

依赖：pip install pymysql python-dotenv
"""
import os
import pymysql
from dotenv import load_dotenv

load_dotenv(encoding="utf-8")

def _db_conf() -> dict:
    """数据库连接参数。做成函数而不是模块级常量：
    数据库现在只用来读 base_url / model 这类非关键项，连不上就退化到默认值，
    不该让 import 本模块这件事直接失败。"""
    return dict(
        host=os.getenv("MYSQL_HOST", "127.0.0.1"),
        port=int(os.getenv("MYSQL_PORT", "3306")),
        user=os.getenv("MYSQL_USER", "root"),
        password=os.getenv("MYSQL_PASSWORD", ""),
        database="datanote",
        charset="utf8mb4",
        cursorclass=pymysql.cursors.DictCursor,
    )


def _read_default_config() -> dict:
    """从 dn_ai_config 读被设为「默认」且启用的那套配置。"""
    try:
        conn = pymysql.connect(**_db_conf())
        try:
            with conn.cursor() as cur:
                cur.execute(
                    "SELECT provider, base_url, model, api_key FROM dn_ai_config "
                    "WHERE is_default=1 AND status=1 LIMIT 1"
                )
                return cur.fetchone() or {}
        finally:
            conn.close()
    except Exception:
        return {}


def get_ai_config() -> dict:
    """
    返回 {"api_key", "base_url", "model"}。
    api_key 只来自本地 .env；base_url / model 优先用页面上配的那套。
    """
    row = _read_default_config()

    # 1. API Key：只读本地 .env，不碰数据库
    api_key = os.getenv("QWEN_API_KEY") or os.getenv("DASHSCOPE_API_KEY") or ""
    if not api_key:
        raise RuntimeError(
            "缺少 API Key。请在 ai-service/.env 中设置 QWEN_API_KEY=你的密钥"
            "（可复制 env.example 修改）。"
        )

    # 2. Base URL：datanote 存的是 .../compatible-mode，OpenAI 兼容端点需补 /v1
    base_url = row.get("base_url") or "https://dashscope.aliyuncs.com/compatible-mode"
    if base_url.rstrip("/").endswith("compatible-mode"):
        base_url = base_url.rstrip("/") + "/v1"

    # 3. Model
    model = row.get("model") or "qwen-plus"

    return {"api_key": api_key, "base_url": base_url, "model": model}


def get_prompt(code: str) -> str:
    """从 dn_prompt 表读取指定 code 的提示词内容；读不到时返回空字符串。"""
    try:
        conn = pymysql.connect(**_db_conf())
        try:
            with conn.cursor() as cur:
                cur.execute("SELECT content FROM dn_prompt WHERE code=%s LIMIT 1", (code,))
                row = cur.fetchone()
                return (row or {}).get("content", "")
        finally:
            conn.close()
    except Exception:
        return ""


if __name__ == "__main__":
    cfg = get_ai_config()
    masked = (cfg["api_key"][:6] + "***" + cfg["api_key"][-4:]) if cfg["api_key"] else "(空)"
    print("从 datanote 读到的 AI 配置：")
    print("  api_key :", masked)
    print("  base_url:", cfg["base_url"])
    print("  model   :", cfg["model"])
