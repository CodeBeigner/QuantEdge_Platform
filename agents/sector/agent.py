"""Sector & Macro Agent — sector rotation, macro regime, breadth analysis."""
from __future__ import annotations

import json
import logging
from datetime import datetime
from typing import List, Optional

from llm.base import LLMMessage, LLMProvider
from llm.deepseek import DeepSeekProvider
from llm.budget import LLMBudget
from llm.sanitizer import sanitize_content
from agents.sector.schema import MacroRegimeReport

_log = logging.getLogger(__name__)

SECTOR_SYSTEM_PROMPT = """You are a Sector & Macro Strategist at a quantitative hedge fund. Analyze the current macro environment and sector dynamics.

Determine:
1. Overall market regime: RISK_ON, RISK_OFF, or NEUTRAL
2. VIX environment assessment (LOW, NORMAL, ELEVATED, EXTREME)
3. Rate environment (RISING, FALLING, STABLE)
4. Credit spread assessment (TIGHT, NORMAL, WIDE)
5. Which sectors show strength vs weakness (list 2-3 leaders and 2-3 laggards)
6. Key implications for equity positioning

Format response as JSON:
{
  "regime": "RISK_ON",
  "vix_level": "NORMAL",
  "rate_environment": "STABLE",
  "credit_spreads": "TIGHT",
  "sector_rotation": [
    {"sector": "Technology", "trend": "leading", "note": "AI-driven momentum"},
    {"sector": "Energy", "trend": "lagging", "note": "Oil price weakness"}
  ],
  "breadth_summary": "2-3 sentence breadth assessment",
  "implications": "2-3 sentence positioning guidance"
}"""


class SectorAnalyst:
    def __init__(
        self,
        provider: Optional[LLMProvider] = None,
        budget: Optional[LLMBudget] = None,
    ):
        self.provider = provider or DeepSeekProvider()
        self.budget = budget or LLMBudget()

    def analyze(self, context: str = "") -> MacroRegimeReport:
        if not self.budget.can_call("sector"):
            return MacroRegimeReport(
                breadth_summary="LLM budget exceeded for sector agent today.",
                timestamp=datetime.utcnow().isoformat(),
            )

        user_prompt = f"""
Analyze the current macro and sector environment.
Context: {sanitize_content(context) if context else "No additional context. Provide general assessment."}
""".strip()[:4000]

        messages = [
            LLMMessage(role="system", content=SECTOR_SYSTEM_PROMPT),
            LLMMessage(role="user", content=user_prompt),
        ]

        try:
            resp = self.provider.chat(messages, temperature=0.3, max_tokens=1024)
            self.budget.record_call("sector", resp.input_tokens, resp.output_tokens, resp.cost_usd)

            if resp.finish_reason == "error":
                return MacroRegimeReport(
                    breadth_summary=f"Analysis failed: {resp.content}",
                    timestamp=datetime.utcnow().isoformat(),
                )

            data = json.loads(extract_json(resp.content))

            return MacroRegimeReport(
                regime=data.get("regime", "NEUTRAL"),
                vix_level=data.get("vix_level", "NORMAL"),
                rate_environment=data.get("rate_environment", "STABLE"),
                credit_spreads=data.get("credit_spreads", "NORMAL"),
                sector_rotation=data.get("sector_rotation", []),
                breadth_summary=data.get("breadth_summary", resp.content[:500]),
                implications=data.get("implications", ""),
                timestamp=datetime.utcnow().isoformat(),
            )

        except Exception as e:
            _log.exception("Sector analysis failed")
            return MacroRegimeReport(
                breadth_summary=f"Error: {e}",
                timestamp=datetime.utcnow().isoformat(),
            )


def extract_json(text: str) -> str:
    text = text.strip()
    if "```json" in text:
        start = text.index("```json") + 7
        end = text.index("```", start)
        return text[start:end].strip()
    if "```" in text:
        start = text.index("```") + 3
        end = text.index("```", start)
        return text[start:end].strip()
    if text.startswith("{"):
        return text
    import re
    match = re.search(r'\{.*\}', text, re.DOTALL)
    return match.group(0) if match else text
