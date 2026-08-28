"""
取数 Agent + 核对 Agent + 状态机编排 —— datanote 需求管理的多 Agent 层
================================================================
多 Agent 设计（见「datanote需求管理设计_MeegoAI.md」九·五）：
  AI 在取数需求的不同阶段承担不同角色，按「需求状态」编排，各 Agent 通过读写
  同一个需求工作项（黑板模式）传递结果，不互相自由对话 —— 可控、可调试、全程留痕。

  状态            上场 Agent        职责                         工具
  ─────────────────────────────────────────────────────────────────
  待澄清/评审  →  需求分析 Agent   聊天澄清 + 产出加工口径/建模建议  RAG + Hive元数据
  出数中       →  取数 Agent       加工口径 → HiveSQL → 执行出数     RAG + 元数据 + SQL执行
  核对中       →  核对 Agent       校验行数/汇总/口径/环比           元数据 + SQL执行

分层：本文件 = 取数/核对 Agent 的「大脑」；prod_chat_service.py = 「服务层」（HTTP + Redis）。
需求分析 Agent 在 requirement_analyst.py，本文件复用它初始化好的模型与 RAG 工具。
"""
import json
import re

from langchain_core.messages import SystemMessage, HumanMessage, ToolMessage

# 复用需求分析 Agent 已初始化好的模型（datanote 默认模型）和 RAG 工具
from requirement_analyst import model, search_knowledge
from datanote_config import get_prompt
from hive_metadata_tool import (
    HIVE_METADATA_TOOLS,
    HIVE_SQL_EXEC_TOOLS,
    search_tables,
    list_tables,
    get_table_schema,
)


# ============ 通用工具循环（与需求分析 Agent 同款，但可绑不同工具集） ============
def _run(system_prompt: str, user_content: str, tools: list, max_rounds: int = 10) -> str:
    """给一段系统人设 + 一条用户输入 + 一组工具，让模型自主调工具直到给出最终文本。
    若工具轮次用尽仍未给出最终文本，强制再要一次「不许调工具」的纯文本收尾。"""
    bound = model.bind_tools(tools)
    tool_map = {t.name: t for t in tools}
    messages = [SystemMessage(content=system_prompt), HumanMessage(content=user_content)]
    ai = None
    finished = False
    for _ in range(max_rounds):
        ai = bound.invoke(messages)
        messages.append(ai)
        tool_calls = getattr(ai, "tool_calls", None)
        if not tool_calls:
            finished = True                       # 模型自然收口（没有再调工具）
            break
        for tc in tool_calls:
            fn = tool_map.get(tc["name"])
            try:
                result = fn.invoke(tc["args"]) if fn else f"未知工具 {tc['name']}"
            except Exception as e:
                result = f"工具执行失败：{e}"
            messages.append(ToolMessage(content=str(result), tool_call_id=tc["id"]))
    # 收尾兜底：轮次耗尽（还想调工具）或最终消息没有文本时，强制用纯模型收口出最终答案
    if not finished or ai is None or not (ai.content or "").strip():
        messages.append(HumanMessage(content="请基于以上信息，现在直接给出最终结果，不要再调用任何工具。"))
        ai = model.invoke(messages)
    return (ai.content or "").strip() or "（模型未产出内容，请重试或补充口径）"


def _extract_json(text: str) -> dict:
    """从模型输出里抠出 JSON 主体（qwen 常用 ```json 包裹）。失败返回 {}。"""
    m = re.search(r"\{.*\}", text, re.S)
    if m:
        try:
            return json.loads(m.group(0))
        except Exception:
            pass
    return {}


def _knowledge_context(query: str, k_hint: str = "") -> str:
    """确定性先查一次 RAG，把知识库内容显式放入上下文，避免模型跳过检索。"""
    q = (query or "").strip()
    if k_hint:
        q = f"{k_hint}\n{q}"
    try:
        return search_knowledge.invoke({"query": q}) or "知识库里暂无相关内容"
    except Exception as e:
        return f"知识库检索失败：{e}"


def _metadata_context(message: str, context: str = "") -> str:
    """确定性预查 Hive 元数据，把真实候选库表放进上下文，降低模型编造表名的概率。"""
    text = f"{message or ''}\n{context or ''}".lower()
    terms = []
    keyword_map = [
        ("信贷", ["loan", "credit"]),
        ("借据", ["借据", "loan"]),
        ("放款", ["放款", "disburse"]),
        ("授信", ["授信", "credit"]),
        ("用信", ["用信", "loan_apply"]),
        ("还款", ["还款", "repay"]),
        ("逾期", ["逾期", "overdue"]),
        ("催收", ["催收", "collection"]),
        ("客户", ["客户", "customer"]),
        ("产品", ["产品", "product"]),
        ("风控", ["风控", "risk"]),
    ]
    for cn, kws in keyword_map:
        if cn in text or any(k in text for k in kws):
            terms.extend(kws)
    if not terms:
        terms = [message[:20]] if message else []

    outputs = []
    seen = set()
    for term in terms:
        if not term or term in seen:
            continue
        seen.add(term)
        try:
            outputs.append(search_tables.invoke({"keyword": term}))
        except Exception as e:
            outputs.append(f"搜索表 {term} 失败：{e}")

    # 信贷域常用 Hive 分层库补一份表清单，保证模型知道真实库表名。
    if any(k in text for k in ("信贷", "借据", "loan", "credit", "授信", "还款", "逾期", "放款")):
        for db in ("ods", "dwd", "dws", "ads", "dim"):
            try:
                outputs.append(list_tables.invoke({"database": db}))
            except Exception as e:
                outputs.append(f"列出库 {db} 失败：{e}")
        for db, table in (
            ("ods", "ods_credit_center_credit_apply_df"),
            ("ods", "ods_loan_center_loan_apply_df"),
            ("dwd", "dwd_risk_event_dtl_df"),
        ):
            try:
                outputs.append(get_table_schema.invoke({"database": db, "table": table}))
            except Exception as e:
                outputs.append(f"查询表结构 {db}.{table} 失败：{e}")
    return "\n\n".join(outputs) if outputs else "未预查到元数据候选"


# ============ 取数 Agent（取数工程师） ============
_QUERY_PROMPT_FALLBACK = """你是一位资深的数仓取数工程师。
上游需求分析师已经产出了结构化的「加工口径 + 建模建议」，你的任务是据此真正把数取出来：

工作步骤（务必按顺序）：
1. 读懂加工口径里的：目标指标、维度、时间范围、过滤条件、粒度。
2. 用 search_tables / get_table_schema 确认要用的真实库表和字段（不要凭空编字段名）。
3. 写一条可执行的 HiveSQL（针对已确认的 Hive 表；聚合别忘了 GROUP BY，注意分区和时间范围过滤）。
4. 用 execute_sql 执行这条 HiveSQL，拿到真实结果。
5. 若报错（字段不存在/语法错），修正 SQL 后重试，最多试几次。

最终【只输出 JSON】，不要多余文字：
{
  "sql": "最终执行成功的 SQL 全文",
  "result_summary": "结果摘要：多少行、关键数值/趋势、口径落实情况。若始终失败，说明卡在哪。"
}"""


def _query_prompt() -> str:
    p = get_prompt("query_agent")
    return p if p.strip() else _QUERY_PROMPT_FALLBACK


def run_query(spec: str, extra: str = "") -> dict:
    """取数 Agent：根据加工口径生成并执行 HiveSQL，返回 {sql, result_summary}。"""
    content = f"这是需求分析师产出的加工口径+建模建议：\n{spec or '（未提供口径，请根据下面的澄清信息推断）'}"
    if extra:
        content += f"\n\n补充上下文：\n{extra}"
    content += "\n\n请据此完成取数，并按要求只输出 JSON。"
    text = _run(_query_prompt(), content, [search_knowledge] + HIVE_METADATA_TOOLS + HIVE_SQL_EXEC_TOOLS)
    data = _extract_json(text)
    if not data:
        return {"sql": "", "result_summary": text}
    return {"sql": data.get("sql", ""), "result_summary": data.get("result_summary", "")}


# ============ 数据开发页 AI 助手（统一走 LLM + RAG + 元数据） ============
_DEV_ASSIST_PROMPT_FALLBACK = """你是 DataNote 数据开发页里的资深数仓开发助手。
所有回答必须基于【RAG知识库内容 + 真实Hive元数据工具】完成，不能只凭通用知识编造表名、字段名或公司口径。

回答风格：
1. 默认只回答用户当前问题的直接答案，不展示推理过程、检索过程、工具调用过程。
2. 默认控制在 3-6 行；能用一句话回答就用一句话。
3. 不主动输出"下一步建议""优势""注意事项""延伸方案""完整字段表格"，除非用户明确要求。
4. 用户要求"只列出/只返回/直接给我"时，严格只输出对应内容。
5. 只有当用户明确要求详细分析、完整方案、完整 SQL、字段清单、口径说明时，才展开结构化长答案。

工作规则：
1. 先阅读调用方已经提供的 RAG 检索结果；如果需要补充口径、命名规范、建模方法论，继续调用 search_knowledge。
2. 只要要写 SQL、推荐用表、解释字段、设计模型，就必须使用【已预查的真实元数据候选】或继续用 search_tables / list_tables / get_table_schema 查真实库表和字段。
3. 写 SQL 时，优先给可落地的 HiveSQL/数仓 SQL；字段依据必须来自 Hive 表名、字段名、类型和注释。
4. 当用户只问设计建议时，先给最直接结论；只有用户追问细节时，再展开模型层级、粒度、主键、核心维度、核心指标、来源表、加工逻辑和风险点。
5. 当用户要求直接写 SQL 时，输出 ```sql 代码块，并在代码后简要说明依赖表和关键口径。
6. 不确定的业务口径要明确列为待确认项，不要拍脑袋。
7. 禁止把未在元数据候选或工具结果里出现过的库名、表名、字段名写成可用依赖。
8. 如果字段注释里提到某个关联表，但该表没有被 list_tables/search_tables/get_table_schema 查到，
   只能标为"注释中提到的潜在关联方向，当前 Hive 元数据未确认存在"，不能用于 SQL。
"""


def _dev_assist_prompt() -> str:
    p = get_prompt("dev_assist")
    return p if p.strip() else _DEV_ASSIST_PROMPT_FALLBACK


def dev_assist(message: str, context: str = "", mode: str = "chat") -> str:
    """数据开发页通用 AI 助手: LLM + RAG + Hive 元数据。"""
    rag = _knowledge_context(message, "数仓建模规范、SQL规范、模型命名规则、指标口径、业务术语")
    metadata = _metadata_context(message, context)
    content = (
        f"【模式】{mode or 'chat'}\n\n"
        f"【用户输入】\n{message or ''}\n\n"
        f"【页面上下文】\n{context or '（无）'}\n\n"
        f"【已检索到的RAG知识】\n{rag}\n\n"
        f"【已预查的真实Hive元数据候选】\n{metadata}\n\n"
        "请基于以上内容和必要的元数据工具给出答案。"
    )
    return _run(_dev_assist_prompt(), content, [search_knowledge] + HIVE_METADATA_TOOLS, max_rounds=10)


_TABLE_NAME_PROMPT_FALLBACK = """你是数仓模型命名专家。
你的任务是根据用户填写的模型信息，结合 RAG 中的模型命名规范，生成一个标准 Hive 表名。

硬性规则：
1. 表名同时也是任务名称，必须简洁、稳定、可读、符合数仓模型语义。
2. 必须优先遵循 RAG 中的命名规则；如果 RAG 没有明确规则，再使用通用规则。
3. 只输出 JSON，不要输出解释文字。
4. JSON 格式：
{
  "table_name": "dwd_xxx_xxx_xxx_di",
  "reason": "一句话说明命名依据"
}
5. table_name 只能包含小写英文字母、数字、下划线。
"""


def _table_name_prompt() -> str:
    p = get_prompt("table_name_agent")
    return p if p.strip() else _TABLE_NAME_PROMPT_FALLBACK


def generate_model_table_name(payload: dict) -> dict:
    """新建 SQL 脚本时生成表名/任务名：模型描述 + RAG 命名规则。"""
    desc = payload.get("description") or payload.get("modelDesc") or ""
    rag_query = (
        "数仓模型命名规则 表命名规范 任务命名规范 "
        f"{payload.get('layer', '')} {payload.get('tableType', '')} "
        f"{payload.get('subject', '')} {payload.get('subSubject', '')} {desc}"
    )
    rag = _knowledge_context(rag_query)
    content = (
        f"【RAG命名规则】\n{rag}\n\n"
        "【模型信息】\n"
        f"- 数仓分层：{payload.get('layer') or ''}\n"
        f"- 表类型：{payload.get('tableType') or ''}\n"
        f"- 主题域：{payload.get('subject') or ''}\n"
        f"- 二级主题：{payload.get('subSubject') or ''}\n"
        f"- 数据库名：{payload.get('dbName') or ''}\n"
        f"- 模型描述：{desc}\n\n"
        "请生成标准表名。"
    )
    text = _run(_table_name_prompt(), content, [search_knowledge], max_rounds=4)
    data = _extract_json(text)
    table_name = (data.get("table_name") or data.get("tableName") or text or "").strip().lower()
    table_name = re.sub(r"[^a-z0-9_]", "", table_name)
    return {
        "table_name": table_name,
        "reason": data.get("reason", "") if data else "",
        "rag": rag,
        "raw": text,
    }


# ============ 核对 Agent（数据质检员） ============
_CHECK_PROMPT_FALLBACK = """你是一位严谨的数据质检员。
取数工程师已经跑出了结果，你的任务是校验这份数据是否可信、是否符合需求口径：

校验维度（用 execute_sql 跑校验查询来验证，别只凭感觉）：
1. 行数是否合理（是否为 0、是否异常暴涨/暴跌）。
2. 关键指标汇总是否对得上（如总额、总笔数）。
3. 口径是否落实（时间范围、过滤条件是否真的生效）。
4. 是否有明显异常（大量 NULL、负数、重复、环比突变）。

给出明确结论，格式：
【核对结论】通过 / 存疑 / 不通过
【依据】逐条说明你校验了什么、结果如何（引用你跑的校验查询和数字）
【建议】若存疑/不通过，指出问题和修正方向"""


def _check_prompt() -> str:
    p = get_prompt("check_agent")
    return p if p.strip() else _CHECK_PROMPT_FALLBACK


def run_check(spec: str, sql: str, result_summary: str) -> dict:
    """核对 Agent：校验出数结果，返回 {check_result}。"""
    content = (
        f"加工口径：\n{spec or '（无）'}\n\n"
        f"取数 SQL：\n{sql or '（无）'}\n\n"
        f"出数结果摘要：\n{result_summary or '（无）'}\n\n"
        f"请对这份数据做核对校验，并按格式给出结论。"
    )
    text = _run(_check_prompt(), content, HIVE_METADATA_TOOLS + HIVE_SQL_EXEC_TOOLS)
    return {"check_result": text}


# ============ 状态机编排（黑板模式：按需求状态派活，不让 Agent 自由协作） ============
def dispatch(status: str, payload: dict) -> dict:
    """根据需求状态把活派给对应 Agent。
    payload 里带该 Agent 需要的黑板字段（spec / sql / result_summary）。
    返回该 Agent 往工作项要写回的字段。"""
    if status == "querying":
        return run_query(payload.get("spec", ""), payload.get("extra", ""))
    if status == "checking":
        return run_check(payload.get("spec", ""), payload.get("sql", ""), payload.get("result_summary", ""))
    return {"error": f"状态 {status} 无对应自动 Agent（澄清/评审阶段用对话 Agent 的 /chat）"}


# ============ 本地自测 ============
if __name__ == "__main__":
    demo_spec = json.dumps({
        "加工口径": {"目标指标": ["订单数"], "维度": ["天"], "时间范围": "近30天", "数据粒度": "汇总"}
    }, ensure_ascii=False)
    print("== 取数 Agent ==")
    q = run_query(demo_spec)
    print(json.dumps(q, ensure_ascii=False, indent=2))
    print("\n== 核对 Agent ==")
    print(run_check(demo_spec, q["sql"], q["result_summary"])["check_result"])
