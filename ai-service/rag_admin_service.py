"""
RAG 向量库管理系统 —— FastAPI + Redis
================================================================
功能：
  · 新建导入：填标题 + 多条文本 → 向量化存入 Redis 向量库，并保留一条导入记录
  · 历史记录：列出每一次导入（标题/时间/条数）
  · 查看明细：点开某条记录，看这次导入了哪些文本
  · 语义检索测试：输入一句话，看能召回哪些文本（验证 RAG 效果）

依赖：
  pip install fastapi "uvicorn[standard]" redis langchain-community langchain-redis dashscope \
    -i https://pypi.tuna.tsinghua.edu.cn/simple

启动：
  uvicorn rag_admin_service:app --reload --port 8001
  浏览器打开 http://localhost:8001
"""
import os
import json
import time
import uuid
from fastapi import FastAPI
from fastapi.responses import HTMLResponse
from pydantic import BaseModel

import redis
from langchain_community.embeddings import DashScopeEmbeddings
from langchain_redis import RedisConfig, RedisVectorStore

# ---------- 配置 ----------
from dotenv import load_dotenv
load_dotenv(encoding="utf-8")         # 加载项目根目录的 .env（里面有 QWEN_API_KEY）

REDIS_URL = "redis://localhost:6379"
INDEX_NAME = "rag_knowledge"          # 向量索引名
DASHSCOPE_KEY = (os.getenv("DASHSCOPE_API_KEY") or os.getenv("QWEN_API_KEY")
                or os.getenv("aliQwen-api"))

# ---------- 启动时构建一次 ----------
from fastapi.middleware.cors import CORSMiddleware
app = FastAPI(title="RAG 向量库管理系统")
# 允许 datanote 页面(8099)跨域调用本服务
app.add_middleware(CORSMiddleware, allow_origins=["*"], allow_methods=["*"], allow_headers=["*"])

# 普通 redis：存"导入批次记录"（标题、时间、原始文本）
rdb = redis.Redis.from_url(REDIS_URL, decode_responses=True)

# 向量库：存向量
embeddings = DashScopeEmbeddings(model="text-embedding-v3", dashscope_api_key=DASHSCOPE_KEY)
vector_store = RedisVectorStore(embeddings, config=RedisConfig(index_name=INDEX_NAME, redis_url=REDIS_URL))

# 聊天模型：用于「更新时 AI 生成变更总结」（用 datanote 网页里配置的默认模型）
try:
    from datanote_config import get_ai_config
    from langchain.chat_models import init_chat_model
    _cfg = get_ai_config()
    _chat_args = {
        "model": _cfg["model"], "model_provider": "openai",
        "api_key": _cfg["api_key"], "base_url": _cfg["base_url"],
    }
    chat_model = init_chat_model(**_chat_args)
except Exception:
    chat_model = None

BATCH_ZSET = "rag:batches"            # 批次索引（按时间排序）
BATCH_KEY = "rag:batch:{}"            # 单个批次记录


def _basic_diff(old_texts, new_texts) -> str:
    """不依赖 AI 的兜底变更摘要：按整条文本比对，统计增删。"""
    olds, news = set(old_texts), set(new_texts)
    added, removed = news - olds, olds - news
    parts = []
    if added:   parts.append(f"新增 {len(added)} 条")
    if removed: parts.append(f"删除 {len(removed)} 条")
    if not parts: parts.append("内容有调整")
    return "本次更新：" + "，".join(parts) + f"（{len(old_texts)} 条 → {len(new_texts)} 条）"


def _summarize_change(old_texts, new_texts) -> str:
    """用 AI 总结「这次更新改了什么」，方便用户一眼看懂版本差异。失败则退回基础统计。"""
    if chat_model is None:
        return _basic_diff(old_texts, new_texts)
    try:
        prompt = (
            "你在帮用户记录知识库的「更新日志」。下面是某条知识【更新前】和【更新后】的内容"
            "（每条一行）。请用简洁中文总结这次更新改了什么：新增/删除了哪些内容、修改了哪些口径或定义。"
            "只输出总结本身，3~5 句以内，不要客套话、不要复述原文。\n\n"
            "【更新前】\n" + ("\n".join(old_texts) if old_texts else "（空）") +
            "\n\n【更新后】\n" + ("\n".join(new_texts) if new_texts else "（空）")
        )
        txt = (chat_model.invoke(prompt).content or "").strip()
        return txt or _basic_diff(old_texts, new_texts)
    except Exception:
        return _basic_diff(old_texts, new_texts)


# ---------- 数据结构 ----------
class ImportRequest(BaseModel):
    title: str            # 本次导入的标题/备注
    texts: list[str]      # 多条文本


class SearchRequest(BaseModel):
    query: str
    k: int = 3


# ---------- 接口：新建导入 ----------
@app.post("/api/batches")
def create_batch(req: ImportRequest):
    texts = [t.strip() for t in req.texts if t and t.strip()]
    if not texts:
        return {"ok": False, "msg": "没有有效文本"}

    batch_id = uuid.uuid4().hex[:12]
    ts = time.time()

    # 1. 向量化存入向量库（每条带 batch_id，便于溯源）；记下返回的向量 key，删除时可精确定位
    metadatas = [{"batch_id": batch_id, "title": req.title} for _ in texts]
    vec_ok, vec_msg, vec_ids = True, "", []
    try:
        vec_ids = list(vector_store.add_texts(texts, metadatas) or [])
    except Exception as e:
        vec_ok, vec_msg = False, str(e)

    # 2. 保存导入批次记录（原始文本 + 向量 key + 版本历史，v1=初始导入）
    record = {
        "id": batch_id,
        "title": req.title,
        "created_at": ts,
        "count": len(texts),
        "texts": texts,
        "vectorized": vec_ok,
        "vector_ids": vec_ids,
        "version": 1,
        "versions": [{
            "version": 1, "op": "create", "created_at": ts,
            "count": len(texts), "texts": texts,
            "summary": f"初始导入 {len(texts)} 条内容。",
        }],
    }
    rdb.set(BATCH_KEY.format(batch_id), json.dumps(record, ensure_ascii=False))
    rdb.zadd(BATCH_ZSET, {batch_id: ts})

    return {"ok": True, "id": batch_id, "count": len(texts),
            "vectorized": vec_ok, "vec_msg": vec_msg}


def _delete_batch_vectors(batch_id: str, stored_ids=None) -> int:
    """精确删除某个批次的所有向量。
    优先用记录里存的向量 key 直接删；老数据没存 key，则兜底 SCAN 每条向量的 batch_id 字段匹配删。
    返回实际删除的向量条数。绝不误伤其他批次。"""
    # 路径①：按记录里存的向量 key 精确删
    if stored_ids:
        keys = list(stored_ids)
        # 兼容 key 带不带索引前缀两种情况
        variants = set(keys) | {(k if str(k).startswith(INDEX_NAME + ":") else f"{INDEX_NAME}:{k}") for k in keys}
        n = rdb.delete(*variants) if variants else 0
        if n:
            return n
    # 路径②：兜底，扫描所有向量，按 batch_id 字段精确匹配
    to_del = [key for key in rdb.scan_iter(match=f"{INDEX_NAME}:*", count=500)
              if rdb.hget(key, "batch_id") == batch_id]
    return rdb.delete(*to_del) if to_del else 0


# ---------- 接口：删除某个批次（删向量 + 删记录）----------
@app.delete("/api/batches/{batch_id}")
def delete_batch(batch_id: str):
    raw = rdb.get(BATCH_KEY.format(batch_id))
    rec = json.loads(raw) if raw else {}
    n = _delete_batch_vectors(batch_id, rec.get("vector_ids"))
    rdb.delete(BATCH_KEY.format(batch_id))
    rdb.zrem(BATCH_ZSET, batch_id)
    return {"ok": True, "deleted_vectors": n}


# ---------- 接口：更新某个批次（删旧向量 + 存新向量，替换内容，保留同一条知识条目）----------
@app.put("/api/batches/{batch_id}")
def update_batch(batch_id: str, req: ImportRequest):
    raw = rdb.get(BATCH_KEY.format(batch_id))
    if not raw:
        return {"ok": False, "msg": "记录不存在"}
    old = json.loads(raw)
    texts = [t.strip() for t in req.texts if t and t.strip()]
    if not texts:
        return {"ok": False, "msg": "没有有效文本"}

    old_texts = old.get("texts", [])
    # 1. AI 生成本次变更总结（更新前 vs 更新后），方便用户看懂改了什么
    summary = _summarize_change(old_texts, texts)
    # 2. 先删旧向量（精确）
    removed = _delete_batch_vectors(batch_id, old.get("vector_ids"))
    # 3. 存新向量（沿用同一个 batch_id，等于原地替换）
    title = req.title.strip() if req.title and req.title.strip() else old.get("title", "")
    metadatas = [{"batch_id": batch_id, "title": title} for _ in texts]
    vec_ok, vec_msg, vec_ids = True, "", []
    try:
        vec_ids = list(vector_store.add_texts(texts, metadatas) or [])
    except Exception as e:
        vec_ok, vec_msg = False, str(e)
    # 4. 追加一条版本记录
    ts = time.time()
    versions = old.get("versions", [])
    new_ver = old.get("version", len(versions)) + 1
    versions.append({
        "version": new_ver, "op": "update", "created_at": ts,
        "count": len(texts), "texts": texts, "summary": summary,
    })
    # 5. 更新批次记录（保留创建时间，记更新时间）
    old.update(title=title, count=len(texts), texts=texts,
               vectorized=vec_ok, vector_ids=vec_ids, updated_at=ts,
               version=new_ver, versions=versions)
    rdb.set(BATCH_KEY.format(batch_id), json.dumps(old, ensure_ascii=False))

    return {"ok": True, "id": batch_id, "count": len(texts), "version": new_ver,
            "removed_vectors": removed, "summary": summary,
            "vectorized": vec_ok, "vec_msg": vec_msg}


# ---------- 接口：某个批次的版本历史 ----------
@app.get("/api/batches/{batch_id}/versions")
def batch_versions(batch_id: str):
    raw = rdb.get(BATCH_KEY.format(batch_id))
    if not raw:
        return {"ok": False, "msg": "记录不存在"}
    rec = json.loads(raw)
    # 老数据可能没有 versions 字段，兜底补一个 v1
    versions = rec.get("versions") or [{
        "version": 1, "op": "create", "created_at": rec.get("created_at"),
        "count": rec.get("count", 0), "texts": rec.get("texts", []),
        "summary": f"初始导入 {rec.get('count', 0)} 条内容。",
    }]
    # 倒序：最新版本在前
    return {"ok": True, "title": rec.get("title", ""), "current": rec.get("version", len(versions)),
            "versions": list(reversed(versions))}


# ---------- 接口：历史记录列表 ----------
@app.get("/api/batches")
def list_batches():
    ids = rdb.zrevrange(BATCH_ZSET, 0, -1)   # 按时间倒序
    out = []
    for bid in ids:
        raw = rdb.get(BATCH_KEY.format(bid))
        if not raw:
            continue
        r = json.loads(raw)
        out.append({"id": r["id"], "title": r["title"],
                    "created_at": r["created_at"],
                    "updated_at": r.get("updated_at", r["created_at"]),
                    "count": r["count"],
                    "version": r.get("version", 1),
                    "vectorized": r.get("vectorized", True)})
    return out


# ---------- 接口：某条批次明细 ----------
@app.get("/api/batches/{batch_id}")
def get_batch(batch_id: str):
    raw = rdb.get(BATCH_KEY.format(batch_id))
    if not raw:
        return {"ok": False, "msg": "记录不存在"}
    return {"ok": True, "batch": json.loads(raw)}


# ---------- 接口：语义检索测试 ----------
@app.post("/api/search")
def search(req: SearchRequest):
    try:
        results = vector_store.similarity_search_with_score(req.query, k=req.k)
        return {"ok": True, "results": [
            {"text": doc.page_content,
             "title": doc.metadata.get("title", ""),
             "score": float(score)} for doc, score in results
        ]}
    except Exception as e:
        return {"ok": False, "msg": str(e)}


# ---------- 页面 ----------
@app.get("/", response_class=HTMLResponse)
def ui():
    return HTML


HTML = """
<!DOCTYPE html>
<html lang="zh"><head><meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>RAG 向量库管理</title>
<style>
  * { box-sizing:border-box; margin:0; padding:0; }
  body { font-family:-apple-system,"PingFang SC",sans-serif; background:#f0f2f5; color:#333; }
  .wrap { max-width:900px; margin:24px auto; padding:0 16px; }
  h1 { font-size:20px; margin-bottom:16px; }
  .card { background:#fff; border-radius:12px; padding:18px; box-shadow:0 2px 10px rgba(0,0,0,.06); margin-bottom:18px; }
  .row { display:flex; gap:10px; align-items:center; margin-bottom:10px; }
  input, textarea { width:100%; border:1px solid #ddd; border-radius:8px; padding:10px 12px; font-size:14px; outline:none; }
  textarea { min-height:140px; resize:vertical; font-family:inherit; }
  button { background:#4f7cff; color:#fff; border:none; border-radius:8px; padding:9px 18px; cursor:pointer; font-size:14px; }
  button.ghost { background:#eef1f8; color:#4f7cff; }
  table { width:100%; border-collapse:collapse; }
  th,td { text-align:left; padding:10px; border-bottom:1px solid #f0f0f0; font-size:14px; }
  th { color:#888; font-weight:500; }
  tr.clickable:hover { background:#f7f9ff; cursor:pointer; }
  .tag { font-size:12px; padding:2px 8px; border-radius:10px; background:#e8f5e9; color:#388e3c; }
  .tag.warn { background:#fff3e0; color:#e65100; }
  .muted { color:#999; font-size:12px; }
  .item { padding:8px 10px; border:1px solid #eee; border-radius:8px; margin-bottom:6px; font-size:14px; white-space:pre-wrap; }
  .hint { font-size:12px; color:#999; margin-top:4px; }
</style></head>
<body><div class="wrap">
  <h1>🗂️ RAG 向量库管理系统</h1>

  <!-- 新建导入 -->
  <div class="card">
    <div class="row"><b>➕ 新建导入</b></div>
    <input id="title" placeholder="本次导入标题 / 备注（如：风控指标口径-2026Q3）">
    <div class="hint">下面每一行是一条文本，换行分隔。会被逐条向量化存入向量库。</div>
    <textarea id="texts" placeholder="每行一条文本，例如：\nGMV 成交总额：已支付订单金额之和，不含退款...\n客单价 = GMV / 下单用户数..."></textarea>
    <div class="row" style="margin-top:10px;">
      <button onclick="doImport()">导入并向量化</button>
      <span id="importMsg" class="muted"></span>
    </div>
  </div>

  <!-- 语义检索测试 -->
  <div class="card">
    <div class="row"><b>🔍 语义检索测试</b></div>
    <div class="row">
      <input id="q" placeholder="输入一句话，看能召回哪些文本（验证 RAG）">
      <button class="ghost" onclick="doSearch()" style="white-space:nowrap;">检索</button>
    </div>
    <div id="searchResult"></div>
  </div>

  <!-- 历史记录 -->
  <div class="card">
    <div class="row"><b>📜 历史导入记录</b> <button class="ghost" onclick="loadList()" style="margin-left:auto;">刷新</button></div>
    <table><thead><tr><th>标题</th><th>时间</th><th>条数</th><th>状态</th></tr></thead>
      <tbody id="list"></tbody></table>
  </div>

  <!-- 明细 -->
  <div class="card" id="detailCard" style="display:none;">
    <div class="row"><b>📄 <span id="detailTitle"></span></b>
      <button class="ghost" onclick="document.getElementById('detailCard').style.display='none'" style="margin-left:auto;">关闭</button></div>
    <div id="detailBody"></div>
  </div>
</div>
<script>
  function fmt(ts){ const d=new Date(ts*1000); return d.toLocaleString(); }

  async function doImport(){
    const title=document.getElementById('title').value.trim();
    const texts=document.getElementById('texts').value.split('\\n').map(s=>s.trim()).filter(Boolean);
    if(!title||texts.length===0){ document.getElementById('importMsg').textContent='请填标题和至少一条文本'; return; }
    document.getElementById('importMsg').textContent='导入中...';
    const res=await fetch('/api/batches',{method:'POST',headers:{'Content-Type':'application/json'},
      body:JSON.stringify({title,texts})});
    const d=await res.json();
    if(d.ok){
      document.getElementById('importMsg').textContent='✅ 已导入 '+d.count+' 条'+(d.vectorized?'（已向量化）':'（向量化失败：'+d.vec_msg+'）');
      document.getElementById('title').value=''; document.getElementById('texts').value='';
      loadList();
    }else{ document.getElementById('importMsg').textContent='❌ '+d.msg; }
  }

  async function loadList(){
    const res=await fetch('/api/batches'); const list=await res.json();
    const tb=document.getElementById('list'); tb.innerHTML='';
    if(list.length===0){ tb.innerHTML='<tr><td colspan="4" class="muted">还没有导入记录</td></tr>'; return; }
    list.forEach(b=>{
      const tr=document.createElement('tr'); tr.className='clickable'; tr.onclick=()=>openDetail(b.id);
      tr.innerHTML='<td>'+b.title+'</td><td class="muted">'+fmt(b.created_at)+'</td><td>'+b.count+'</td>'+
        '<td>'+(b.vectorized?'<span class="tag">已向量化</span>':'<span class="tag warn">未向量化</span>')+'</td>';
      tb.appendChild(tr);
    });
  }

  async function openDetail(id){
    const res=await fetch('/api/batches/'+id); const d=await res.json();
    if(!d.ok){ return; }
    const b=d.batch;
    document.getElementById('detailTitle').textContent=b.title+'（'+b.count+' 条 · '+fmt(b.created_at)+'）';
    document.getElementById('detailBody').innerHTML=b.texts.map((t,i)=>'<div class="item">'+(i+1)+'. '+t+'</div>').join('');
    document.getElementById('detailCard').style.display='block';
    document.getElementById('detailCard').scrollIntoView({behavior:'smooth'});
  }

  async function doSearch(){
    const query=document.getElementById('q').value.trim(); if(!query) return;
    document.getElementById('searchResult').innerHTML='<span class="muted">检索中...</span>';
    const res=await fetch('/api/search',{method:'POST',headers:{'Content-Type':'application/json'},
      body:JSON.stringify({query,k:3})});
    const d=await res.json();
    if(d.ok){
      document.getElementById('searchResult').innerHTML=d.results.map(r=>
        '<div class="item">'+r.text+'<div class="muted">来源：'+r.title+' · 距离：'+r.score.toFixed(4)+'</div></div>').join('')
        || '<span class="muted">无结果</span>';
    }else{ document.getElementById('searchResult').innerHTML='<span class="muted">❌ '+d.msg+'</span>'; }
  }

  loadList();
</script>
</body></html>
"""
