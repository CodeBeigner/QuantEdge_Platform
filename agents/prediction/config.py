"""Prediction Aggregator configuration."""
from __future__ import annotations

import os
from dataclasses import dataclass


@dataclass
class PredictionConfig:
    min_confidence: float = float(os.getenv("PREDICTION_MIN_CONFIDENCE", "0.55"))
    kronos_weight: float = float(os.getenv("PREDICTION_KRONOS_WEIGHT", "0.40"))
    llm_weight: float = float(os.getenv("PREDICTION_LLM_WEIGHT", "0.35"))
    research_weight: float = float(os.getenv("PREDICTION_RESEARCH_WEIGHT", "0.25"))
    default_horizon_hours: int = int(os.getenv("PREDICTION_HORIZON_HOURS", "24"))
    tp_pct: float = float(os.getenv("PREDICTION_TP_PCT", "0.04"))
    sl_pct: float = float(os.getenv("PREDICTION_SL_PCT", "0.02"))
