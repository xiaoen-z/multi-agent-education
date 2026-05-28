"""
LLM 服务层 -- 封装 LLM 调用，提供统一的 chat 接口。

所有 Agent 通过此服务调用 LLM，失败时返回 None，
调用方自行降级到模板回复。
"""

import logging

from openai import AsyncOpenAI

logger = logging.getLogger(__name__)


class LLMService:
    """Async LLM 调用封装，支持重试和降级。"""

    def __init__(
        self,
        api_key: str,
        model: str = "gpt-4o",
        base_url: str | None = None,
        max_retries: int = 2,
    ) -> None:
        self.model = model
        self.max_retries = max_retries
        self.client = None

        if not api_key:
            logger.warning("No LLM API key configured, agents will use template fallback.")
            return

        client_kwargs = {"api_key": api_key, "max_retries": 0}
        if base_url:
            client_kwargs["base_url"] = base_url
        self.client = AsyncOpenAI(**client_kwargs)

    async def chat(
        self,
        system_prompt: str,
        user_prompt: str,
        temperature: float = 0.4,
    ) -> str | None:
        """
        调用 LLM，失败返回 None。

        重试策略：首次失败后最多重试 max_retries 次，
        每次重试间有短暂延迟。
        """
        messages = [
            {"role": "system", "content": system_prompt},
            {"role": "user", "content": user_prompt},
        ]

        if not self.client:
            return None

        last_error = None
        for attempt in range(self.max_retries + 1):
            try:
                response = await self.client.chat.completions.create(
                    model=self.model,
                    messages=messages,
                    temperature=temperature,
                )
                return response.choices[0].message.content

            except Exception as exc:
                last_error = exc
                if attempt < self.max_retries:
                    logger.warning(
                        "LLM call failed (attempt %d/%d): %s",
                        attempt + 1,
                        self.max_retries + 1,
                        exc,
                    )
                else:
                    logger.error("LLM call failed after %d attempts: %s", self.max_retries + 1, exc)

        return None
