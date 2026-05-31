"""Prediction Aggregator — ensemble probability estimation from Kronos + research + LLM."""
from __future__ import annotations

import json
import logging
import time
from datetime import datetime, timezone
from typing import List, Optional

from llm.base import LLMMessage, LLMProvider
from llm.deepseek import DeepSeekProvider
from llm.budget import LLMBudget
from llm.sanitizer import sanitize_content
from agents.prediction.config import PredictionConfig
from agents.prediction.schema import PredictionSignal, AggregationResult
from agents.prediction.calibration import CalibrationTracker

_log = logging.getLogger(__name__)

PREDICTION_SYSTEM_PROMPT = """You are a Prediction Aggregator at a quantitative hedge fund. Your job is to synthesize multiple inputs into a single calibrated probability estimate.

Inputs you receive:
- Kronos forecast return (% over horizon)
- Fundamental analyst report (DCF valuation)
- Earnings/catalyst signal (beat/miss, guidance)
- Sentiment signal (bullish/bearish narrative)
- Macro regime context

Synthesize these into:
1. Direction (LONG, SHORT, or HOLD)
2. Probability of being correct (0.5-1.0)
3. Confidence in the synthesis (0.0-1.0, lower if inputs conflict)
4. Key rationale (what drove the decision)

IMPORTANT: If inputs conflict, lower your confidence. Do not overstate certainty.

Format response as JSON:
{
  "direction": "LONG",
  "probability": 0.65,
  "confidence": 0.70,
  "rationale": "Kronos forecast +2.3% with bullish fundamentals. Earnings upgrade supports. Sentiment neutral — not a concern.",
  "conflict_detected": false,
  "conflict_detail": ""
}"""


class PredictionAggregator:
    def __init__(
        self,
        config: Optional[PredictionConfig] = None,
        provider: Optional[LLMProvider] = None,
        budget: Optional[LLMBudget] = None,
    ):
        self.config = config or PredictionConfig()
        self.provider = provider or DeepSeekProvider()
        self.budget = budget or LLMBudget()
        self.calibration = CalibrationTracker()

    def aggregate(
        self,
        asset: str,
        entry_price: float,
        kronos_forecast: Optional[dict] = None,
        fundamental_report: Optional[dict] = None,
        earnings_signal: Optional[dict] = None,
        sentiment_signal: Optional[dict] = None,
        macro_context: Optional[str] = None,
    ) -> AggregationResult:
        start = time.time()

        if not self.budget.can_call("prediction"):
            return AggregationResult(
                signal=PredictionSignal(
                    asset=asset,
                    direction="HOLD",
                    entry_price=entry_price,
                    rationale="LLM budget exceeded",
                    timestamp=datetime.now(timezone.utc).isoformat(),
                ),
                processing_time_ms=(time.time() - start) * 1000,
            )

        kronos_return = kronos_forecast.get("forecast_return") if kronos_forecast else None
        model_prob = self._kronos_to_probability(kronos_return)

        context = self._build_context(
            asset, kronos_forecast, fundamental_report,
            earnings_signal, sentiment_signal, macro_context,
        )

        prompt = f"""
Analyze the opportunity for {asset} at ${entry_price:.2f}.

{context}
""".strip()

        messages = [
            LLMMessage(role="system", content=PREDICTION_SYSTEM_PROMPT),
            LLMMessage(role="user", content=prompt),
        ]

        try:
            resp = self.provider.chat(messages, temperature=0.3, max_tokens=1024)
            self.budget.record_call("prediction", resp.input_tokens, resp.output_tokens, resp.cost_usd)

            if resp.finish_reason == "error":
                return AggregationResult(
                    signal=PredictionSignal(
                        asset=asset, direction="HOLD", entry_price=entry_price,
                        rationale=f"LLM error: {resp.content}",
                        timestamp=datetime.now(timezone.utc).isoformat(),
                    ),
                    model_probability=model_prob,
                    processing_time_ms=(time.time() - start) * 1000,
                )

            data = json.loads(self._extract_json(resp.content))
            llm_prob = data.get("probability", 0.5)
            llm_confidence = data.get("confidence", 0.5)

            ensemble_prob = self._compute_ensemble(
                model_prob, llm_prob,
                fundamental_report, earnings_signal, sentiment_signal,
            )

            direction = data.get("direction", "HOLD")
            if direction == "HOLD" or ensemble_prob < self.config.min_confidence:
                direction = "HOLD"
                ensemble_prob = max(ensemble_prob, 0.5)

            entry = entry_price
            tp = entry * (1 + self.config.tp_pct) if direction == "LONG" else entry * (1 - self.config.tp_pct)
            sl = entry * (1 - self.config.sl_pct) if direction == "LONG" else entry * (1 + self.config.sl_pct)

            signal = PredictionSignal(
                asset=asset,
                direction=direction,
                probability=round(ensemble_prob, 4),
                confidence=round(llm_confidence, 4),
                horizon_hours=self.config.default_horizon_hours,
                entry_price=entry_price,
                stop_loss=sl,
                take_profit=tp,
                rationale=data.get("rationale", resp.content[:500]),
                sources=["kronos", "fundamental", "earnings", "sentiment", "sector"],
                agent_contributions={
                    "kronos": round(self.config.kronos_weight * model_prob, 4),
                    "llm": round(self.config.llm_weight * llm_prob, 4),
                    "research": round(self.config.research_weight, 4),
                },
                timestamp=datetime.now(timezone.utc).isoformat(),
            )

            return AggregationResult(
                signal=signal,
                input_count=sum(1 for x in [kronos_forecast, fundamental_report, earnings_signal, sentiment_signal] if x),
                model_probability=model_prob,
                llm_probability=llm_prob,
                ensemble_probability=ensemble_prob,
                processing_time_ms=round((time.time() - start) * 1000, 1),
            )

        except Exception as e:
            _log.exception("Prediction aggregation failed for %s", asset)
            return AggregationResult(
                signal=PredictionSignal(
                    asset=asset, direction="HOLD", entry_price=entry_price,
                    rationale=f"Error: {e}",
                    timestamp=datetime.now(timezone.utc).isoformat(),
                ),
                model_probability=model_prob,
                processing_time_ms=(time.time() - start) * 1000,
            )

    def _kronos_to_probability(self, forecast_return: Optional[float]) -> float:
        if forecast_return is None:
            return 0.5
        if forecast_return > 0.05:
            return min(0.9, 0.5 + forecast_return * 5)
        elif forecast_return < -0.05:
            return max(0.1, 0.5 + forecast_return * 5)
        return 0.5

    def _compute_ensemble(
        self,
        model_prob: float,
        llm_prob: float,
        fundamental: Optional[dict] = None,
        earnings: Optional[dict] = None,
        sentiment: Optional[dict] = None,
    ) -> float:
        research_signal = 0.5
        adjustments = 0

        if fundamental:
            rec = fundamental.get("recommendation", "HOLD")
            if rec == "BUY":
                research_signal += 0.1
                adjustments += 1
            elif rec == "SELL":
                research_signal -= 0.1
                adjustments += 1

        if earnings:
            verdict = earnings.get("verdict", "NEUTRAL")
            if verdict == "UPGRADE":
                research_signal += 0.08
                adjustments += 1
            elif verdict == "DOWNGRADE":
                research_signal -= 0.08
                adjustments += 1

        if sentiment:
            sent = sentiment.get("sentiment", "NEUTRAL")
            if sent == "BULLISH":
                research_signal += 0.05
                adjustments += 1
            elif sent == "BEARISH":
                research_signal -= 0.05
                adjustments += 1

        research_signal = max(0.1, min(0.9, research_signal))

        ensemble = (
            self.config.kronos_weight * model_prob
            + self.config.llm_weight * llm_prob
            + self.config.research_weight * research_signal
        )

        return round(max(0.0, min(1.0, ensemble)), 4)

    def record_outcome(self, agent: str, asset: str, predicted_prob: float, was_correct: bool):
        self.calibration.record(agent, asset, predicted_prob, 1 if was_correct else 0)

    def _build_context(self, asset, kronos, fundamental, earnings, sentiment, macro) -> str:
        parts = []
        if kronos:
            ret = kronos.get("forecast_return")
            if ret is not None:
                parts.append(f"Kronos Forecast: {ret:+.2f}% return over horizon")
        if fundamental:
            parts.append(
                f"Fundamental: {fundamental.get('recommendation', 'HOLD')} "
                f"(upside: {fundamental.get('upside_pct', 0):+.1f}%, "
                f"confidence: {fundamental.get('confidence', 0):.0%})"
            )
        if earnings:
            parts.append(
                f"Earnings: {earnings.get('verdict', 'NEUTRAL')} "
                f"({earnings.get('beats_misses', 'N/A')})"
            )
        if sentiment:
            parts.append(
                f"Sentiment: {sentiment.get('sentiment', 'NEUTRAL')} "
                f"(confidence: {sentiment.get('confidence', 0):.0%})"
            )
        if macro:
            parts.append(f"Macro: {sanitize_content(macro)[:500]}")
        return "\n".join(parts)

    @staticmethod
    def _extract_json(text: str) -> str:
        import re
        text = text.strip()
        for delim in ["```json", "```"]:
            if delim in text:
                start = text.index(delim) + len(delim)
                end = text.index("```", start)
                return text[start:end].strip()
        if text.startswith("{"):
            return text
        match = re.search(r'\{.*\}', text, re.DOTALL)
        return match.group(0) if match else text
