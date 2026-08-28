"""
生产版对话服务 demo —— FastAPI + Redis 持久化记忆
================================================================
展示"练习脚本 → 生产服务"的演进：

  · 模型只在【服务启动时】初始化一次（复用 qwen_llm 的单例），不是每条消息都重建
  · 每条用户消息 = 一次 HTTP 请求（POST /chat），服务一直运行、逐条处理
  · 用 session_id 区分不同用户，各自独立历史，互不干扰
  · 历史存 Redis，服务重启 / 多实例部署 记忆都不丢

依赖安装：
    pip install fastapi "uvicorn[standard]" redis langchain-community \
      -i https://pypi.tuna.tsinghua.edu.cn/simple

启动服务：
    uvicorn prod_chat_service:app --reload
    #        ↑文件名(不含.py)  ↑FastAPI 实例名

测试方式一（浏览器）：打开 http://localhost:8000/docs ，点 /chat 在线调
测试方式二（命令行）：
    curl -X POST http://localhost:8000/chat \
      -H "Content-Type: application/json" \
      -d '{"session_id": "user-001", "message": "我叫张三"}'
    curl -X POST http://localhost:8000/chat \
      -H "Content-Type: application/json" \
      -d '{"session_id": "user-001", "message": "我叫什么？"}'   # 它会记得
"""
from fastapi import FastAPI
from fastapi.responses import HTMLResponse
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
from langchain_community.chat_message_histories import RedisChatMessageHistory

# 需求分析 Agent（大脑）：本服务是它的「服务层」，只负责 HTTP 接口 + Redis 记忆
from requirement_analyst import analyze, generate_spec, finalize
# 取数 Agent + 核对 Agent + 状态机编排（多 Agent 层）
from data_agents import run_query, run_check, dispatch, dev_assist, generate_model_table_name

REDIS_URL = "redis://localhost:6379"

# ============ 服务：需求管理的对话入口 ============
app = FastAPI(title="需求管理 · 需求分析 Agent 服务")
# 允许 datanote 页面(8099)跨域调用本服务
app.add_middleware(CORSMiddleware, allow_origins=["*"], allow_methods=["*"], allow_headers=["*"])


# 历史存取（Redis，多轮记忆）—— 每条需求/每个用户一个 session_id
def get_history(session_id: str) -> RedisChatMessageHistory:
    return RedisChatMessageHistory(session_id=session_id, url=REDIS_URL)


# ============ 请求 / 响应数据结构 ============
class ChatRequest(BaseModel):
    session_id: str
    message: str


class ChatResponse(BaseModel):
    reply: str


class SpecRequest(BaseModel):
    session_id: str


class QueryRequest(BaseModel):
    session_id: str = ""
    spec: str = ""          # 加工口径（JSON 字符串）


class CheckRequest(BaseModel):
    session_id: str = ""
    spec: str = ""
    sql: str = ""
    result_summary: str = ""


class DispatchRequest(BaseModel):
    status: str             # 需求当前状态，决定派给哪个 Agent
    spec: str = ""
    sql: str = ""
    result_summary: str = ""


class AssistRequest(BaseModel):
    session_id: str = "dev-assistant-default"
    message: str
    context: str = ""
    mode: str = "chat"


class TableNameRequest(BaseModel):
    layer: str = ""
    tableType: str = ""
    subject: str = ""
    subSubject: str = ""
    description: str = ""
    dbName: str = ""


# ============ 接口 1：澄清对话（交给需求分析 Agent）============
@app.post("/chat", response_model=ChatResponse)
def chat(req: ChatRequest):
    store = get_history(req.session_id)
    # 把"之前的对话 + 这次输入"交给需求分析 Agent，它会自主调 RAG / MySQL 工具来澄清
    reply = analyze(req.message, list(store.messages))
    # 持久化本轮到 Redis（下次接着聊有记忆）
    store.add_user_message(req.message)
    store.add_ai_message(reply)
    return ChatResponse(reply=reply)


# ============ 接口 2：生成加工口径 + 建模建议 ============
# 基于这条需求的整段澄清对话，产出给数仓看的结构化交付物
@app.post("/spec")
def spec(req: SpecRequest):
    store = get_history(req.session_id)
    return {"ok": True, "spec": generate_spec(list(store.messages))}


# ============ 接口 2.5：把澄清对话凝练成「一条完善的需求」============
# 新建需求流程：先和 AI 聊清楚，再 finalize 出 标题+完善描述+加工口径，据此建卡
@app.post("/finalize")
def finalize_req(req: SpecRequest):
    store = get_history(req.session_id)
    msgs = list(store.messages)
    if not msgs:
        return {"ok": False, "msg": "还没有对话内容，请先描述你的取数需求"}
    return {"ok": True, **finalize(msgs)}


# ============ 接口 3：取数 Agent（加工口径 → SQL → 执行出数）============
@app.post("/query")
def query(req: QueryRequest):
    try:
        return run_query(req.spec)
    except Exception as e:
        return {"sql": "", "result_summary": "", "error": f"取数 Agent 出错：{e}"}


# ============ 接口 4：核对 Agent（校验行数/汇总/口径/环比）============
@app.post("/check")
def check(req: CheckRequest):
    try:
        return run_check(req.spec, req.sql, req.result_summary)
    except Exception as e:
        return {"check_result": "", "error": f"核对 Agent 出错：{e}"}


# ============ 接口 5：状态机编排（按需求状态自动派活给对应 Agent）============
@app.post("/dispatch")
def dispatch_agent(req: DispatchRequest):
    try:
        return dispatch(req.status, req.model_dump())
    except Exception as e:
        return {"error": f"编排出错：{e}"}


# ============ 接口 6：数据开发页 AI 助手（LLM + RAG + 元数据）============
@app.post("/assist", response_model=ChatResponse)
def assist(req: AssistRequest):
    store = get_history(req.session_id or "dev-assistant-default")
    history_context = "\n".join(
        f"{getattr(m, 'type', 'message')}: {getattr(m, 'content', '')}"
        for m in list(store.messages)[-8:]
    )
    merged_context = (req.context or "").strip()
    if history_context:
        merged_context = (merged_context + "\n\n" if merged_context else "") + "【最近对话】\n" + history_context
    reply = dev_assist(req.message, merged_context, req.mode)
    store.add_user_message(req.message)
    store.add_ai_message(reply)
    return ChatResponse(reply=reply)


# ============ 接口 7：新建 SQL 脚本表名/任务名生成（模型描述 + RAG命名规则）============
@app.post("/generate-table-name")
def generate_table_name(req: TableNameRequest):
    data = generate_model_table_name(req.model_dump())
    return {"ok": True, **data}


# ============ 聊天页面（好看的聊天框 UI）============
CHAT_HTML = """
<!DOCTYPE html>
<html lang="zh">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>AI 对话</title>
<style>
  * { box-sizing: border-box; margin: 0; padding: 0; }
  body { font-family: -apple-system, "PingFang SC", "Microsoft YaHei", sans-serif;
         background: #eef1f6; height: 100vh; display: flex; align-items: center; justify-content: center; }
  .chat { width: 440px; height: 680px; background: #fff; border-radius: 18px;
          box-shadow: 0 10px 40px rgba(0,0,0,.14); display: flex; flex-direction: column; overflow: hidden; }
  .header { background: linear-gradient(135deg,#4f7cff,#7a5cff); color:#fff; padding: 18px 22px; font-weight:600; font-size:16px; }
  .header small { display:block; font-weight:400; opacity:.85; font-size:12px; margin-top:3px; }
  .messages { flex:1; padding:18px; overflow-y:auto; background:#f7f8fa; }
  .msg { margin-bottom:14px; display:flex; }
  .msg.user { justify-content:flex-end; }
  .bubble { max-width:76%; padding:10px 14px; border-radius:14px; line-height:1.55; font-size:14px;
            white-space:pre-wrap; word-break:break-word; }
  .msg.ai .bubble { background:#fff; border:1px solid #eaeaea; border-bottom-left-radius:4px; color:#333; }
  .msg.user .bubble { background:#4f7cff; color:#fff; border-bottom-right-radius:4px; }
  .inputbar { display:flex; padding:14px; border-top:1px solid #eee; gap:10px; }
  .inputbar input { flex:1; border:1px solid #ddd; border-radius:22px; padding:11px 18px; font-size:14px; outline:none; }
  .inputbar input:focus { border-color:#4f7cff; }
  .inputbar button { background:#4f7cff; color:#fff; border:none; border-radius:22px; padding:0 22px; cursor:pointer; font-size:14px; }
  .inputbar button:disabled { opacity:.5; cursor:not-allowed; }
</style>
</head>
<body>
  <div class="chat">
    <div class="header">AI 助手 <small>会话：user-001 · 带 Redis 记忆</small></div>
    <div class="messages" id="messages"></div>
    <div class="inputbar">
      <input id="input" placeholder="输入消息，回车发送…" autocomplete="off">
      <button id="send">发送</button>
    </div>
  </div>
<script>
  const SESSION_ID = "user-001";   // 真实项目从登录态取，这里固定演示
  const messagesEl = document.getElementById("messages");
  const inputEl = document.getElementById("input");
  const sendBtn = document.getElementById("send");

  function addMsg(text, who) {
    const div = document.createElement("div");
    div.className = "msg " + who;
    div.innerHTML = '<div class="bubble"></div>';
    div.querySelector(".bubble").textContent = text;
    messagesEl.appendChild(div);
    messagesEl.scrollTop = messagesEl.scrollHeight;
    return div;
  }

  async function send() {
    const text = inputEl.value.trim();
    if (!text) return;
    inputEl.value = "";
    addMsg(text, "user");
    sendBtn.disabled = true;
    const thinking = addMsg("思考中…", "ai");
    try {
      const res = await fetch("/chat", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ session_id: SESSION_ID, message: text })
      });
      const data = await res.json();
      thinking.querySelector(".bubble").textContent = data.reply || ("出错：" + JSON.stringify(data));
    } catch (e) {
      thinking.querySelector(".bubble").textContent = "请求失败：" + e;
    } finally {
      sendBtn.disabled = false;
      inputEl.focus();
      messagesEl.scrollTop = messagesEl.scrollHeight;
    }
  }

  sendBtn.onclick = send;
  inputEl.addEventListener("keydown", e => { if (e.key === "Enter") send(); });
  inputEl.focus();
</script>
</body>
</html>
"""


@app.get("/", response_class=HTMLResponse)
def chat_ui():
    return CHAT_HTML
