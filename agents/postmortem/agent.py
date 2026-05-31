"""Post-Mortem Agent — classifies closed trades and surfaces learnings."""
from __future__ import annotations

import logging
from datetime import datetime
from typing import List, Optional

from agents.postmortem.schema import PostMortemResult
from data.ledger.schema import TradeRecord

_log = logging.getLogger(__name__)


class PostMortemAgent:
    CLASSIFICATION_RULES = {
        "model_error": [
            "probability below 0.55",
            "confidence low",
            "counter-trend",
            "no fundamental support",
        ],
        "timing_error": [
            "entered too early",
            "exited too early",
            "held too long",
            "signal decay",
        ],
        "execution_error": [
            "slippage",
            "fill issue",
            "partial execution",
            "liquidity",
        ],
        "external_shock": [
            "pre-market unexpected",
            "macro surprise",
            "geopolitical event",
            "sector-wide move",
        ],
    }

    def analyze(self, record: TradeRecord, market_context: str = "") -> PostMortemResult:
        classification, explanation = self._classify(record, market_context)

        record.outcome_class = classification

        lessons = self._generate_lessons(record, classification)

        return PostMortemResult(
            trade_id=record.trade_id,
            asset=record.asset,
            outcome=record.outcome,
            classification=classification,
            explanation=explanation,
            lessons=lessons,
            pnl=record.pnl,
        )

    def _classify(self, record: TradeRecord, context: str) -> tuple:
        if record.model_probability < 0.55:
            return "model_error", f"Low model probability ({record.model_probability:.2f}) — signal should not have been taken"

        if record.pnl > 0 and record.outcome == "WIN":
            return "model_success", "Prediction was correct"

        if record.time_held_hours < 1 and record.outcome == "LOSS":
            return "timing_error", f"Position held only {record.time_held_hours:.1f}h — possible premature entry"

        if "slippage" in context.lower() or "fill" in context.lower():
            return "execution_error", "Execution issue indicated in context"

        if any(kw in context.lower() for kw in ["unexpected", "surprise", "macro", "geopolitical"]):
            return "external_shock", "External event likely caused the loss"

        return "model_error", "Model prediction was incorrect for this trade"

    def _generate_lessons(self, record: TradeRecord, classification: str) -> List[str]:
        lessons = []
        if classification == "model_error":
            lessons.append(f"Review {record.asset} model signals — probability {record.model_probability:.2f} insufficient")
        elif classification == "timing_error":
            lessons.append(f"Consider confirmation window before entering {record.asset}")
        elif classification == "execution_error":
            lessons.append(f"Review execution quality for {record.asset} — check slippage threshold")
        elif classification == "external_shock":
            lessons.append(f"Consider pre-event position reduction for {record.asset}")
        elif classification == "model_success":
            lessons.append(f"Signal worked for {record.asset} — pattern worth reinforcing")
        return lessons

    def consolidate(self, trades: List[TradeRecord]) -> dict:
        if not trades:
            return {"total": 0, "classifications": {}, "total_pnl": 0.0, "key_learnings": []}

        counts: dict = {}
        total_pnl = 0.0
        all_lessons = []

        for trade in trades:
            result = self.analyze(trade)
            counts[result.classification] = counts.get(result.classification, 0) + 1
            total_pnl += trade.pnl
            all_lessons.extend(result.lessons)

        unique_lessons = list(dict.fromkeys(all_lessons))

        return {
            "total": len(trades),
            "classifications": counts,
            "total_pnl": round(total_pnl, 2),
            "avg_pnl": round(total_pnl / len(trades), 2) if trades else 0.0,
            "key_learnings": unique_lessons[:10],
            "consolidation_time": datetime.utcnow().isoformat(),
        }
