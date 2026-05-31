"""Earnings/Catalyst Agent — tracks events, analyzes transcripts via LLM."""
from __future__ import annotations

import json
import logging
from datetime import datetime, timedelta
from typing import List, Optional

from llm.base import LLMMessage, LLMProvider
from llm.deepseek import DeepSeekProvider
from llm.budget import LLMBudget
from llm.sanitizer import sanitize_content
from agents.earnings.schema import EarningsSignal

_log = logging.getLogger(__name__)

EARNINGS_SYSTEM_PROMPT = """You are an Earnings Analyst at a quantitative hedge fund. Analyze the provided earnings transcript or press release summary.

Extract:
1. Beats/misses vs consensus (EPS and Revenue)
2. Guidance changes (raised/maintained/lowered)
3. Management tone (bullish/bearish/neutral)
4. Key points (top 3 takeaways)
5. Overall verdict: UPGRADE (positive), DOWNGRADE (negative), or NEUTRAL

Format your response as JSON:
{
  "beats_misses": "Beat EPS by 3%, Missed Revenue by 1%",
  "guidance_change": "Raised FY guidance by 2%",
  "tone": "BULLISH",
  "key_points": ["Strong Services growth", "China weakness noted", "Buyback increased"],
  "verdict": "UPGRADE",
  "confidence": 0.75,
  "summary": "Brief 2-3 sentence assessment"
}"""


class EarningsAnalyst:
    def __init__(
        self,
        provider: Optional[LLMProvider] = None,
        budget: Optional[LLMBudget] = None,
    ):
        self.provider = provider or DeepSeekProvider()
        self.budget = budget or LLMBudget()
        self._upcoming_events: List[dict] = []

    def add_event(self, symbol: str, event_date: str, event_type: str = "earnings"):
        self._upcoming_events.append({
            "symbol": symbol,
            "event_date": event_date,
            "event_type": event_type,
        })

    def get_upcoming(self, symbol: Optional[str] = None, days: int = 7) -> List[dict]:
        today = datetime.utcnow().strftime("%Y-%m-%d")
        events = self._upcoming_events
        if symbol:
            events = [e for e in events if e["symbol"] == symbol]
        return [e for e in events if today <= e["event_date"]]

    def analyze(
        self,
        symbol: str,
        transcript_text: str,
        current_price: float = 0.0,
    ) -> EarningsSignal:
        if not self.budget.can_call("earnings"):
            return EarningsSignal(
                symbol=symbol,
                summary="LLM budget exceeded for earnings agent today.",
                verdict="NEUTRAL",
                timestamp=datetime.utcnow().isoformat(),
            )

        user_prompt = f"""
Analyze earnings for {symbol} at price ${current_price:.2f}.

Transcript/Summary:
{sanitize_content(transcript_text)[:8000]}
""".strip()

        messages = [
            LLMMessage(role="system", content=EARNINGS_SYSTEM_PROMPT),
            LLMMessage(role="user", content=user_prompt),
        ]

        try:
            resp = self.provider.chat(messages, temperature=0.3, max_tokens=1024)
            self.budget.record_call("earnings", resp.input_tokens, resp.output_tokens, resp.cost_usd)

            if resp.finish_reason == "error":
                return EarningsSignal(
                    symbol=symbol,
                    summary=f"Analysis failed: {resp.content}",
                    verdict="NEUTRAL",
                    timestamp=datetime.utcnow().isoformat(),
                )

            data = json.loads(extract_json(resp.content))

            return EarningsSignal(
                symbol=symbol,
                event_type="earnings",
                beats_misses=data.get("beats_misses", ""),
                guidance_change=data.get("guidance_change", ""),
                tone=data.get("tone", "NEUTRAL"),
                key_points=data.get("key_points", []),
                verdict=data.get("verdict", "NEUTRAL"),
                confidence=data.get("confidence", 0.5),
                summary=data.get("summary", resp.content[:500]),
                timestamp=datetime.utcnow().isoformat(),
            )

        except Exception as e:
            _log.exception("Earnings analysis failed for %s", symbol)
            return EarningsSignal(
                symbol=symbol,
                summary=f"Error: {e}",
                verdict="NEUTRAL",
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
