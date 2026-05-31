"""Prediction Aggregator output schema."""
from __future__ import annotations

from dataclasses import dataclass, field
from typing import Dict, List, Optional


@dataclass
class PredictionSignal:
    asset: str
    direction: str = "HOLD"  # LONG, SHORT, HOLD
    probability: float = 0.5
    confidence: float = 0.0
    horizon_hours: int = 24
    entry_price: float = 0.0
    stop_loss: Optional[float] = None
    take_profit: Optional[float] = None
    edge: Optional[float] = None  # Phase 2: p_model - p_market
    rationale: str = ""
    sources: List[str] = field(default_factory=list)
    agent_contributions: Dict[str, float] = field(default_factory=dict)
    brier_score: Optional[float] = None
    timestamp: str = ""


@dataclass
class AggregationResult:
    signal: PredictionSignal
    input_count: int = 0
    model_probability: float = 0.5
    llm_probability: Optional[float] = None
    ensemble_probability: float = 0.5
    processing_time_ms: float = 0.0
