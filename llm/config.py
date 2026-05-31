"""LLM provider configuration."""
from __future__ import annotations

import os
from dataclasses import dataclass, field
from typing import Dict


@dataclass
class LLMConfig:
    deepseek_api_key: str = os.getenv("DEEPSEEK_API_KEY", "")
    deepseek_base_url: str = os.getenv("DEEPSEEK_BASE_URL", "https://api.deepseek.com/v1")
    deepseek_model: str = os.getenv("DEEPSEEK_MODEL", "deepseek-chat")
    anthropic_api_key: str = os.getenv("ANTHROPIC_API_KEY", "")
    daily_budget_usd: float = float(os.getenv("LLM_DAILY_BUDGET", "0.66"))
    max_tokens_per_call: int = int(os.getenv("LLM_MAX_TOKENS", "4096"))
    default_temperature: float = float(os.getenv("LLM_TEMPERATURE", "0.3"))

    input_price_per_1k: float = 0.00014   # DeepSeek: $0.14/M input
    output_price_per_1k: float = 0.00028  # DeepSeek: $0.28/M output

    agent_budgets: Dict[str, float] = field(default_factory=lambda: {
        "fundamental": 0.20,
        "earnings": 0.15,
        "sentiment": 0.10,
        "sector": 0.10,
        "prediction": 0.11,
    })
