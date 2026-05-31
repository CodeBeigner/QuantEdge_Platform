"""Pydantic models for stress-test API."""
from __future__ import annotations

from typing import List, Optional
from pydantic import BaseModel, Field


class TradeLogEntry(BaseModel):
    date: str
    pnl: float
    return_pct: float


class StressTestRequest(BaseModel):
    trade_log: List[TradeLogEntry] = Field(..., min_items=30)
    n_simulations: int = Field(default=10000, ge=1000, le=100000)
    n_states: int = Field(default=3, ge=2, le=5)
    backtest_sharpe: Optional[float] = None


class RegimePerformance(BaseModel):
    avg_return: float
    win_rate: float


class MaxDrawdownDistribution(BaseModel):
    p10: float
    p50: float
    p90: float


class MonteCarloResult(BaseModel):
    median_return: float
    var_95: float
    cvar_95: float
    max_drawdown_distribution: MaxDrawdownDistribution
    ruin_probability: float


class StressTestResponse(BaseModel):
    regime_labels: List[int]
    monte_carlo: MonteCarloResult
    overfitting_warning: bool
    regime_performance: dict
