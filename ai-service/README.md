# DataNote AI Service（Python）

DataNote 平台的 AI 能力后端。datanote 主体是 Java（Spring Boot，端口 8099），
AI 相关能力用 Python 实现，作为独立服务运行，被 datanote 前端页面调用。

## 目录内容

| 文件 | 作用 | 端口 |
|------|------|------|
| `prod_chat_service.py` | 需求管理 · 对话 Agent（RAG + Hive 元数据工具 + Redis 记忆） | 8000 |
| `rag_admin_service.py` | AI 智能 · 知识库管理（向量库导入 / 检索 / 历史） | 8001 |
| `hive_metadata_tool.py` | Agent 用的 Hive 元数据查询工具（库/表/字段/注释/分区） | — |
| `mysql_metadata_tool.py` | 旧版 MySQL 元数据工具，保留给业务库排查场景备用 | — |
| `datanote_config.py` | 读取 datanote「系统管理 → AI 配置」里的 API Key/URL/Model（AES 解密） | — |
| `qwen_llm.py` | 通义大模型单例工具（备用） | — |
| `.env` | 本地密钥（QWEN_API_KEY 等），**不提交 git** | — |

## 依赖

被以下服务依赖：
- **MySQL**（datanote-mysql，localhost:3306）：读取 Hive Metastore、AI 配置、用户会话
- **Redis**（localhost:6379）：向量库 + 对话记忆
- **datanote 应用**（8099）：前端页面调用这两个服务

## 安装

```bash
pip install -r requirements.txt -i https://pypi.tuna.tsinghua.edu.cn/simple
```

## 启动

```bash
./start-ai.sh          # 一键起两个服务（8000 + 8001）
# 或分别启动：
uvicorn prod_chat_service:app --reload --port 8000
uvicorn rag_admin_service:app --reload --port 8001
```

## AI Key 从哪读

优先读 datanote「系统管理 → AI 配置」里配置的 key（数据库加密存储，见 `datanote_config.py`），
读不到时回退 `.env` 里的 `QWEN_API_KEY`。

> 安全提醒：`CRYPTO_KEY` 没有默认值，首次部署必须自行生成一把强随机密钥：
> `LC_ALL=C tr -dc 'A-Za-z0-9' < /dev/urandom | head -c 32; echo`
> 填进 `datanote.conf`（该文件不入库）。API Key 则单独放在 `ai-service/.env` 里。
> Python 侧通过环境变量 `DATANOTE_CRYPTO_KEY` 提供同一把密钥。
