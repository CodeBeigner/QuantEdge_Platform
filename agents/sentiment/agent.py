"""Sentiment & News Agent — NLP sentiment scoring from news/Reddit/other sources."""
from __future__ import annotations

import json
import logging
from datetime import datetime
from typing import List, Optional

from llm.base import LLMMessage, LLMProvider
from llm.deepseek import DeepSeekProvider
from llm.budget import LLMBudget
from llm.sanitizer import sanitize_content
from agents.sentiment.schema import SentimentSignal

_log = logging.getLogger(__name__)

SENTIMENT_SYSTEM_PROMPT = """You are a Sentiment Analyst at a quantitative hedge fund. Analyze the provided news headlines and social media snippets for a specific stock.

Determine:
1. Overall sentiment: BULLISH, BEARISH, or NEUTRAL
2. Confidence score (0.0-1.0)
3. Top 3 key themes or narratives
4. Whether the narrative aligns with or diverges from the stock's recent price action

Format response as JSON:
{
  "sentiment": "BULLISH",
  "confidence": 0.72,
  "key_themes": ["AI growth narrative", "Margin expansion", "Regulatory risk"],
  "narrative_summary": "2-3 sentence overall sentiment assessment",
  "narrative_vs_price": "Narrative bullish but price flat — potential signal that market has not priced in the positives"
}"""


class SentimentAnalyst:
    def __init__(
        self,
        provider: Optional[LLMProvider] = None,
        budget: Optional[LLMBudget] = None,
    ):
        self.provider = provider or DeepSeekProvider()
        self.budget = budget or LLMBudget()

    def analyze(
        self,
        symbol: str,
        headlines: List[str],
        current_price: float = 0.0,
        price_change_pct: float = 0.0,
    ) -> SentimentSignal:
        if not self.budget.can_call("sentiment"):
            return SentimentSignal(
                symbol=symbol,
                narrative_summary="LLM budget exceeded for sentiment agent today.",
                timestamp=datetime.utcnow().isoformat(),
            )

        combined_text = "\n".join(f"- {sanitize_content(h)}" for h in headlines[:20])

        user_prompt = f"""
Analyze sentiment for {symbol}. Current price: ${current_price:.2f} ({price_change_pct:+.1f}% change).

Headlines/Sources:
{combined_text[:6000]}
""".strip()

        messages = [
            LLMMessage(role="system", content=SENTIMENT_SYSTEM_PROMPT),
            LLMMessage(role="user", content=user_prompt),
        ]

        try:
            resp = self.provider.chat(messages, temperature=0.3, max_tokens=1024)
            self.budget.record_call("sentiment", resp.input_tokens, resp.output_tokens, resp.cost_usd)

            if resp.finish_reason == "error":
                return SentimentSignal(
                    symbol=symbol,
                    narrative_summary=f"Analysis failed: {resp.content}",
                    timestamp=datetime.utcnow().isoformat(),
                )

            data = json.loads(extract_json(resp.content))

            return SentimentSignal(
                symbol=symbol,
                sentiment=data.get("sentiment", "NEUTRAL"),
                confidence=data.get("confidence", 0.5),
                source_count=len(headlines),
                key_themes=data.get("key_themes", []),
                narrative_summary=data.get("narrative_summary", resp.content[:500]),
                narrative_vs_price=data.get("narrative_vs_price", ""),
                timestamp=datetime.utcnow().isoformat(),
            )

        except Exception as e:
            _log.exception("Sentiment analysis failed for %s", symbol)
            return SentimentSignal(
                symbol=symbol,
                narrative_summary=f"Error: {e}",
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
