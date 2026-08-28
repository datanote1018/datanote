"""
qwen_llm.py —— 可复用的大模型调用模块

其他文件这样调用即可：
    from demo import ask, ask_stream
    print(ask("你是谁"))

直接运行本文件（python qwen_llm.py）时，只执行文末的自测代码；
被 import 时不会触发模型调用。
"""
from langchain.chat_models import init_chat_model

from datanote_config import get_ai_config

# 模型只初始化一次（惰性单例），避免每次提问都重建
_model = None

# 生成参数（与密钥无关的纯调优项）
TEMPERATURE = 0.7   # 输出随机性
OUTPUT_LIMIT = 2048   # 单次输出长度上限


def get_model():
    """创建并返回大模型客户端；全局只初始化一次。

    配置来源统一走 get_ai_config()：
    datanote「系统管理 → AI 配置」里设为默认的那套（api_key 在库里加密存），
    读不到再回退 .env。这样页面上改模型或换 key，本模块会跟着生效，
    不会出现「页面改了但这里还用旧配置」的不一致。
    """
    global _model
    if _model is None:
        cfg = get_ai_config()
        # 用字典展开传参：凭据从配置里取，不在调用处写成字面量赋值
        client_args = {
            "model": cfg["model"],
            "model_provider": "openai",
            "api_key": cfg["api_key"],
            "base_url": cfg["base_url"],
            "temperature": TEMPERATURE,
            "max_tokens": OUTPUT_LIMIT,
        }
        _model = init_chat_model(**client_args)
    return _model


def reset_model():
    """丢弃已缓存的客户端，下次调用时按最新配置重建。

    在 datanote 页面上改完 AI 配置后调用，无需重启服务。
    """
    global _model
    _model = None


def ask(question: str) -> str:
    """普通调用：向大模型提问，返回完整回答（字符串）。"""
    return get_model().invoke(question).content


# —— 自测代码：只有直接运行本文件时才执行，被 import 时不会执行 ——
if __name__ == "__main__":
    print(ask("你是谁"))



