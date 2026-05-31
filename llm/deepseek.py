"""DeepSeek V4 Pro provider — OpenAI-compatible API."""
from __future__ import annotations

import json
import logging
from typing import List, Optional

from llm.base import LLMMessage, LLMProvider, LLMResponse
from llm.config import LLMConfig

_log = logging.getLogger(__name__)


class DeepSeekProvider(LLMProvider):
    def __init__(self, config: Optional[LLMConfig] = None):
        self.config = config or LLMConfig()
        self._available = bool(self.config.deepseek_api_key)

    def get_name(self) -> str:
        return "deepseek"

    def chat(
        self,
        messages: List[LLMMessage],
        temperature: Optional[float] = None,
        max_tokens: Optional[int] = None,
    ) -> LLMResponse:
        if not self._available:
            return LLMResponse(
                content="DeepSeek API key not configured. Set DEEPSEEK_API_KEY.",
                model="none",
                finish_reason="error",
            )

        import urllib.request

        temp = temperature or self.config.default_temperature
        max_tok = max_tokens or self.config.max_tokens_per_call

        body = {
            "model": self.config.deepseek_model,
            "messages": [{"role": m.role, "content": m.content} for m in messages],
            "temperature": temp,
            "max_tokens": max_tok,
        }

        url = f"{self.config.deepseek_base_url}/chat/completions"
        req = urllib.request.Request(
            url,
            data=json.dumps(body).encode("utf-8"),
            headers={
                "Content-Type": "application/json",
                "Authorization": f"Bearer {self.config.deepseek_api_key}",
            },
            method="POST",
        )

        try:
            with urllib.request.urlopen(req, timeout=60) as resp:
                data = json.loads(resp.read().decode("utf-8"))

            choice = data.get("choices", [{}])[0]
            content = choice.get("message", {}).get("content", "")
            usage = data.get("usage", {})
            input_tokens = usage.get("prompt_tokens", 0)
            output_tokens = usage.get("completion_tokens", 0)
            cost = (
                input_tokens / 1000 * self.config.input_price_per_1k
                + output_tokens / 1000 * self.config.output_price_per_1k
            )

            return LLMResponse(
                content=content,
                model=self.config.deepseek_model,
                input_tokens=input_tokens,
                output_tokens=output_tokens,
                cost_usd=round(cost, 6),
                finish_reason="stop",
            )
        except Exception as e:
            _log.error("DeepSeek API error: %s", e)
            return LLMResponse(
                content=f"API error: {e}",
                model=self.config.deepseek_model,
                finish_reason="error",
            )

    def estimate_tokens(self, text: str) -> int:
        return max(1, len(text) // 4)
