#!/bin/bash
# 一键启动 DataNote AI 服务（对话 Agent + RAG 管理）
# 用法：./start-ai.sh          启动
#      ./start-ai.sh stop     停止

set -e
DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$DIR"

# 数据库连接等参数从 datanote.conf 继承（与主应用共用一份配置）。
# API Key 不在这里 —— 它只放在 ai-service/.env 里，由 python-dotenv 自行加载。
CONF="$DIR/../datanote.conf"
if [ -f "$CONF" ]; then
  set -a
  # shellcheck disable=SC1090
  . "$CONF"
  set +a
fi

if [ ! -f "$DIR/.env" ]; then
  echo "错误：缺少 $DIR/.env"
  echo "      请执行：cp env.example .env    然后填入 QWEN_API_KEY"
  exit 1
fi

# Python 解释器（按需改成你的环境）
PY="${PYTHON_BIN:-/opt/miniconda3/envs/langchain/bin/python}"

if [ "$1" = "stop" ]; then
  echo "停止 AI 服务..."
  pkill -f "uvicorn prod_chat_service:app" 2>/dev/null && echo "  已停 对话Agent(8000)" || echo "  对话Agent 未在运行"
  pkill -f "uvicorn rag_admin_service:app" 2>/dev/null && echo "  已停 RAG管理(8001)" || echo "  RAG管理 未在运行"
  exit 0
fi

echo "启动 DataNote AI 服务（Python: $PY）..."

"$PY" -m uvicorn prod_chat_service:app --port 8000 > /tmp/ai-chat.log 2>&1 &
echo "  ▶ 对话Agent(需求管理) → http://localhost:8000  日志 /tmp/ai-chat.log"

"$PY" -m uvicorn rag_admin_service:app --port 8001 > /tmp/ai-rag.log 2>&1 &
echo "  ▶ RAG管理(AI智能)     → http://localhost:8001  日志 /tmp/ai-rag.log"

sleep 3
echo "启动完成。配合 datanote(8099) 使用：需求管理 / AI智能 两个页面。"
