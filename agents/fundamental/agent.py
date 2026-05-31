"""Fundamental Analyst Agent — DCF, comps, valuation."""
from __future__ import annotations

import logging
from datetime import datetime
from typing import List, Optional

from llm.base import LLMMessage, LLMProvider
from llm.deepseek import DeepSeekProvider
from llm.budget import LLMBudget
from llm.sanitizer import sanitize_content
from agents.fundamental.schema import FundamentalReport, ValuationRange, CompData

_log = logging.getLogger(__name__)

FUNDAMENTAL_SYSTEM_PROMPT = """You are a Fundamental Analyst at a quantitative hedge fund. Analyze the given company and produce a structured valuation report.

For the company, compute:
1. DCF valuation with WACC range 10-12%, projecting revenue growth from recent trends
2. Comparable company analysis (list 3-4 peers with EV/EBITDA, EV/Revenue, P/E multiples)
3. Bear/Base/Bull implied price scenarios
4. A clear BUY/SELL/HOLD recommendation

Format your response as JSON with these fields:
{
  "company_name": "...",
  "dcf_revenue_growth": 0.XX,
  "bear_price": XX.XX,
  "base_price": XX.XX,
  "bull_price": XX.XX,
  "comps": [{"ticker": "AAPL", "ev_ebitda": 15.2, "ev_revenue": 6.5, "pe_ratio": 28.0}],
  "summary": "2-3 sentence assessment",
  "recommendation": "BUY",
  "confidence": 0.XX
}"""


class FundamentalAnalyst:
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
        current_price: float,
        context: str = "",
    ) -> FundamentalReport:
        if not self.budget.can_call("fundamental"):
            return FundamentalReport(
                symbol=symbol,
                summary="LLM budget exceeded for fundamental agent today.",
                recommendation="HOLD",
                timestamp=datetime.utcnow().isoformat(),
            )

        user_prompt = f"""
Analyze {symbol} at current price ${current_price:.2f}.
Context: {sanitize_content(context) if context else "No additional context provided."}
""".strip()

        messages = [
            LLMMessage(role="system", content=FUNDAMENTAL_SYSTEM_PROMPT),
            LLMMessage(role="user", content=user_prompt),
        ]

        try:
            resp = self.provider.chat(messages, temperature=0.3, max_tokens=2048)
            self.budget.record_call("fundamental", resp.input_tokens, resp.output_tokens, resp.cost_usd)

            if resp.finish_reason == "error":
                return FundamentalReport(
                    symbol=symbol,
                    summary=f"Analysis failed: {resp.content}",
                    recommendation="HOLD",
                    timestamp=datetime.utcnow().isoformat(),
                )

            import json
            data = json.loads(extract_json(resp.content))

            comps = [
                CompData(
                    ticker=c.get("ticker", ""),
                    ev_ebitda=c.get("ev_ebitda"),
                    ev_revenue=c.get("ev_revenue"),
                    pe_ratio=c.get("pe_ratio"),
                )
                for c in data.get("comps", [])
            ]

            implied = ValuationRange(
                bear=data.get("bear_price", current_price * 0.8),
                base=data.get("base_price", current_price),
                bull=data.get("bull_price", current_price * 1.2),
            )

            upside = ((implied.base - current_price) / current_price * 100) if current_price > 0 else 0

            return FundamentalReport(
                symbol=symbol,
                company_name=data.get("company_name", symbol),
                current_price=current_price,
                implied_price=implied,
                upside_pct=round(upside, 1),
                comps=comps,
                dcf_revenue_growth=data.get("dcf_revenue_growth", 0.0),
                summary=data.get("summary", resp.content[:500]),
                recommendation=data.get("recommendation", "HOLD"),
                confidence=data.get("confidence", 0.5),
                timestamp=datetime.utcnow().isoformat(),
            )

        except Exception as e:
            _log.exception("Fundamental analysis failed for %s", symbol)
            return FundamentalReport(
                symbol=symbol,
                summary=f"Error: {e}",
                recommendation="HOLD",
                timestamp=datetime.utcnow().isoformat(),
            )


def extract_json(text: str) -> str:
    """Extract JSON from LLM response that may contain markdown."""
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
    if match:
        return match.group(0)
    return text
