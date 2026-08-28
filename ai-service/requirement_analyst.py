"""
需求分析 Agent —— datanote 需求管理的「需求分析师」角色
================================================================
它是什么：
  多 Agent 设计里的第一个专职 Agent。人设 = 资深数仓需求分析师。
  职责 = 和业务同学聊天，把"模糊的取数需求"一步步聊清楚，
        最终产出数仓同学能直接看懂的【加工口径 + 建模建议】，降低需求评审成本。

它怎么干活：
  · 用「澄清清单」驱动追问（目标指标 / 口径歧义 / 维度 / 时间 / 过滤 / 粒度）
  · 用 RAG 工具查已有指标口径 —— 发现"成交额""活跃用户"这类歧义词，主动让业务选
  · 用 Hive 元数据工具查数仓真实库表 —— 确认字段是否存在、可复用哪些表
  · 聊清楚后，一键产出结构化的【加工口径 + 建模建议】

分层说明：
  本文件 = Agent 的「大脑」（人设 prompt + 工具 + 决策循环 + 产出）
  prod_chat_service.py = 「服务层」（FastAPI 接口 + Redis 记忆），调用本文件

后续的「取数 Agent」「核对 Agent」照本文件的模板加即可（换 prompt、换工具）。
"""
import json
import re

from langchain.chat_models import init_chat_model
from langchain_core.messages import SystemMessage, HumanMessage, ToolMessage
from langchain_core.tools import tool
from langchain_community.embeddings import DashScopeEmbeddings
from langchain_redis import RedisConfig, RedisVectorStore

from datanote_config import get_ai_config, get_prompt   # 读 datanote「AI配置」和提示词
from hive_metadata_tool import HIVE_METADATA_TOOLS     # 查 Hive 库/表/字段/注释 的工具

REDIS_URL = "redis://localhost:6379"
RAG_INDEX = "rag_knowledge"                     # 和知识库管理页同一个向量索引

# ============ 1. 模型：用 datanote 网页里配置的默认模型 ============
_cfg = get_ai_config()                          # {api_key, base_url, model}
# 用字典展开传参：凭据从配置里取，调用处不写成字面量赋值
_chat_args = {
    "model": _cfg["model"], "model_provider": "openai",
    "api_key": _cfg["api_key"], "base_url": _cfg["base_url"],
}
model = init_chat_model(**_chat_args)

# ============ 2. RAG 检索工具（查业务知识 / 指标口径） ============
_embed_args = {"model": "text-embedding-v3", "dashscope_api_key": _cfg["api_key"]}
_embeddings = DashScopeEmbeddings(**_embed_args)
_vector_store = RedisVectorStore(_embeddings, config=RedisConfig(index_name=RAG_INDEX, redis_url=REDIS_URL))


@tool
def search_knowledge(query: str) -> str:
    """检索公司内部业务知识库（指标口径、指标体系方法论、业务规则、术语定义）。
    以下情况【必须先调用】本工具，基于检索结果回答，不要只凭自己的通用知识：
    - 业务问到任何概念/定义/方法论/术语，例如"什么是指标体系""XX是什么意思""XX方法论"
    - 要确认某个指标怎么算、有没有已定义的口径、口径是否有歧义
    - 任何可能在公司知识库里已有沉淀的问题
    传入要查询的关键词或问题原文即可。"""
    try:
        docs = _vector_store.similarity_search(query, k=3)
        if not docs:
            return "知识库里暂无相关口径"
        return "\n".join(f"- {d.page_content}" for d in docs)
    except Exception as e:
        return f"知识库检索失败：{e}"


# 需求分析 Agent 能用的全部工具 = RAG检索 + Hive元数据
TOOLS = [search_knowledge] + HIVE_METADATA_TOOLS
model_with_tools = model.bind_tools(TOOLS)
TOOL_MAP = {t.name: t for t in TOOLS}

# ============ 3. 人设 + 行为规范（Agent 的灵魂，蒸馏自《需求分析Agent设计》）============
# 每次 analyze/generate_spec/finalize 调用时动态读取，修改 DB 无需重启服务
_ANALYST_SYSTEM_PROMPT_FALLBACK = """你是一位资深的数仓需求分析师。业务同学会用大白话提取数需求，你的任务是：
通过尽量少的追问，把需求拆解成一份【指标详细口径】，让数仓研发能直接看懂、评审、开发。

你产出的是「口径」，不是 SQL，也不由你拍板用哪张表——用哪张表是后续数仓开发时的事，
你只在最后给一句方向性建议。

【回复风格——严格遵守】
- 每次回复不超过 120 字，超出必须截断
- 一次只问 1 个问题，最多 2 个，不要列清单
- 不重复已经聊清楚的内容，不做背景介绍
- 已知信息直接说结论，未知才追问
- 禁止输出"好的""明白了""收到"等无意义开场白

【你的知识来源】
遇到指标体系相关的概念、定义、方法（指标类型、原子指标、修饰词、维度退化、分层归属等），
先调用 search_knowledge 查公司知识库里的《指标体系方法论》，基于它来判断和拆解，
不要只凭自己的通用知识。
涉及表名、字段名、字段注释、分区字段时，必须以 Hive 元数据工具查到的结果为准。
字段注释中提到但 Hive 元数据未查到的关联表，只能作为潜在关联方向，不能当作已存在表。

【重点只抓这 4 个口径要素】（其余业务说清了就直接采纳，别重复问）
1. 原子指标：原子指标有哪些，属于什么业务流程。
   比率类指标（逾期率、转化率、占比）必须拆成分子/分母分别确认，
   并注意分子分母的时间口径可能不一致（如逾期率分子按账龄月、分母按放款月）。
2. 修饰词和时间周期：修饰词的口径你不要举例子，直接说自己的理解，然后让业务确定是否补充。
   例：「新客」是首次放款？「金额」不含退款、核销
3. 范围与过滤：排不排测试数据、只取成功/有效，这部分需要根据上面确定大致用表之后再看表的元数据和使用说明再进一步确定。
4. 维度和粒度：确定需要什么纬度，通常维度确定了，粒度也就确定了
"""


def _get_analyst_prompt() -> str:
    """动态从 DB 读取提示词，读不到则用 fallback。"""
    db_prompt = get_prompt("requirement_analyst")
    return db_prompt if db_prompt.strip() else _ANALYST_SYSTEM_PROMPT_FALLBACK


# 产出【指标口径文档】用的指令（结构对齐《需求分析Agent设计》第16步）
_SPEC_PROMPT_FALLBACK = """请基于以上整段对话，把需求整理成一份结构化的【指标口径文档】。
只输出 JSON，不要多余文字，格式如下（没聊到的项可留空或省略，不要编造）：
{
  "指标": {
    "名称": "如 新客余额",
    "类型": "事务型 / 存量型 / 复合型",
    "复合公式": "仅复合型填，如 逾期本金 / 放款本金；否则留空"
  },
  "口径": {
    "原子指标": "到底算什么，如 借据当前未还本金",
    "修饰词口径": ["修饰词=确切定义，如 新客=首次放款用户"],
    "时间周期": "按哪个时间字段+周期+时点还是区间，如 每日快照时点",
    "范围与过滤": ["排除测试用户", "只取有效借据", "按user_id去重"]
  },
  "粒度": "一行代表什么，如 每天 + 渠道 + 产品 一行",
  "维度": ["渠道", "产品"],
  "待业务确认": ["确定是否符合预期"],
  "方向建议": "给数仓的一句话方向（不拍板具体表名）：如 可能涉及借据明细/快照类、用户标签类表，具体表名和落层由开发确认"
}"""


def _get_spec_prompt() -> str:
    p = get_prompt("requirement_spec_prompt")
    return p if p.strip() else _SPEC_PROMPT_FALLBACK


# ============ 4. Agent 主循环：模型决定调工具 → 执行 → 回喂 → 直到给出答案 ============
def _run_tool_loop(messages: list, max_rounds: int = 6) -> str:
    """通用的"工具调用循环"：给一串消息，让模型自主调工具、拿结果、再推理，最后返回文本回复。
    若轮次耗尽仍想调工具（或最终没有文本），强制收口：不再调工具，直接给业务一句正经回复，
    避免返回"让我再搜一下…"这类半截话导致前端看起来卡住。"""
    ai = None
    finished = False
    for _ in range(max_rounds):                 # 最多 6 轮，防止死循环
        ai = model_with_tools.invoke(messages)
        messages.append(ai)
        tool_calls = getattr(ai, "tool_calls", None)
        if not tool_calls:                      # 没有要调的工具 = 模型已给出最终回复
            finished = True
            break
        for tc in tool_calls:                   # 逐个执行模型点名的工具
            fn = TOOL_MAP.get(tc["name"])
            try:
                result = fn.invoke(tc["args"]) if fn else f"未知工具 {tc['name']}"
            except Exception as e:
                result = f"工具执行失败：{e}"
            messages.append(ToolMessage(content=str(result), tool_call_id=tc["id"]))
    # 兜底：轮次耗尽（还想调工具）或没产出文本时，强制用纯模型收口
    if not finished or ai is None or not (ai.content or "").strip():
        messages.append(HumanMessage(content=(
            "不要再调用任何工具了。基于目前已知的信息，"
            "直接用中文追问业务 1~2 个最关键、还没确定的口径问题，不要输出任何分析过程。")))
        ai = model.invoke(messages)
    return (ai.content or "").strip() or "（模型未产出内容，请重试）"


_EXTRACT_PROMPT = """下面是数仓分析师的内部输出，请按规则处理后直接输出，不要输出任何分类标签或说明文字：

- 如果内容是一份结构化的需求文档（含指标名称、原子指标、时间口径等字段），原样返回文档正文，不加任何前缀
- 如果内容是需要向业务确认的问题，只提取问题本身（1～2 个，直接用序号列出），去掉所有前置分析段落和开场白

严禁：
- 输出"类型A""类型B"等分类标签
- 修改字段名、表名、枚举值（必须与原文完全一致，不得改写、翻译或替换）
- 编造任何不在原文中的字段名或枚举值

内部输出：
{analysis}"""


def analyze(user_msg: str, history: list) -> str:
    """
    一轮需求澄清对话。
    参数：user_msg = 业务这次说的话；history = 之前的对话消息列表（BaseMessage）。
    返回：分析师的回复文本。（history 会被就地追加本轮的 user 和 ai 消息）
    """
    from langchain_core.messages import AIMessage

    # 第一步：模型带工具自由分析（完整推理，不限格式）
    messages = [SystemMessage(content=_get_analyst_prompt())] + list(history) + [HumanMessage(content=user_msg)]
    full_analysis = _run_tool_loop(messages)

    # 第二步：轻量 extraction，只抠出追问的问题给用户看
    extract_msg = [HumanMessage(content=_EXTRACT_PROMPT.format(analysis=full_analysis))]
    clean_reply = (model.invoke(extract_msg).content or "").strip()
    reply = clean_reply if clean_reply else full_analysis

    # 历史存完整分析（保证下轮上下文质量），但用户只看到 reply（纯问题）
    history.append(HumanMessage(content=user_msg))
    history.append(AIMessage(content=full_analysis))
    return reply


def generate_spec(history: list) -> dict:
    """
    基于整段澄清对话，产出结构化的【加工口径 + 建模建议】。
    返回：dict（解析失败时返回 {"raw": 原始文本}）。
    """
    messages = [SystemMessage(content=_get_analyst_prompt())] + list(history) + [HumanMessage(content=_get_spec_prompt())]
    text = _run_tool_loop(messages)
    # qwen 有时会用 ```json 包裹，这里把 JSON 主体抠出来再解析
    m = re.search(r"\{.*\}", text, re.S)
    if m:
        try:
            return json.loads(m.group(0))
        except Exception:
            pass
    return {"raw": text}


# 把整段对话凝练成「一条完善的需求」用的指令
_FINALIZE_PROMPT_FALLBACK = """请基于以上整段澄清对话，把它凝练成一条【完善的取数需求】。
只输出 JSON，不要多余文字，格式如下：
{
  "title": "一句话需求标题（简洁，20字内，能一眼看懂要什么数）",
  "description": "整理后的完善需求描述：用条理清晰的语言归纳业务目标、目标指标及口径、维度、时间范围、过滤条件、数据粒度、数据用途等已澄清的信息。业务原话里模糊的地方，用澄清后的结论替代；未提及的项可省略。"
}"""


def _get_finalize_prompt() -> str:
    p = get_prompt("requirement_finalize_prompt")
    return p if p.strip() else _FINALIZE_PROMPT_FALLBACK


def finalize(history: list) -> dict:
    """
    把整段澄清对话凝练成「一条完善的需求」：标题 + 完善描述 + 结构化加工口径。
    返回 {"title", "description", "spec"}；spec 为 generate_spec 的结构化结果。
    """
    spec = generate_spec(history)
    # 标题/描述是纯归纳，不需要工具，直接用基础模型收口
    messages = [SystemMessage(content=_get_analyst_prompt())] + list(history) + [HumanMessage(content=_get_finalize_prompt())]
    text = (model.invoke(messages).content or "").strip()
    title, description = "", ""
    m = re.search(r"\{.*\}", text, re.S)
    if m:
        try:
            d = json.loads(m.group(0))
            title = (d.get("title") or "").strip()
            description = (d.get("description") or "").strip()
        except Exception:
            pass
    if not description:                 # 兜底：解析失败就把原文当描述
        description = text
    return {"title": title, "description": description, "spec": spec}


# ============ 5. 本地自测：命令行体验一次"聊清楚 → 出口径" ============
if __name__ == "__main__":
    print("需求分析 Agent 自测（输入 quit 退出，输入 spec 生成加工口径）\n")
    hist = []
    while True:
        q = input("业务> ").strip()
        if q in ("quit", "exit"):
            break
        if q == "spec":
            print("分析师> 【加工口径+建模建议】：")
            print(json.dumps(generate_spec(hist), ensure_ascii=False, indent=2))
            continue
        print("分析师>", analyze(q, hist), "\n")
