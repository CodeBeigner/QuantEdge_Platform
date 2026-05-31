"""Pillar C configuration and shared data types."""
from __future__ import annotations

import os
from dataclasses import dataclass, field
from enum import Enum
from typing import List, Optional


class Direction(Enum):
    LONG = "LONG"
    SHORT = "SHORT"
    HOLD = "HOLD"


@dataclass
class PredictionSignal:
    asset: str
    direction: Direction
    probability: float          # win probability (0.0-1.0)
    confidence: float           # ensemble confidence (0.0-1.0)
    entry_price: float
    stop_loss: Optional[float] = None
    take_profit: Optional[float] = None
    rationale: str = ""


@dataclass
class SizedOrder:
    asset: str
    direction: Direction
    size_dollars: float
    entry_price: float
    order_type: str = "LIMIT"
    stop_loss: Optional[float] = None
    take_profit: Optional[float] = None
    expiry_minutes: int = 120


@dataclass
class PortfolioState:
    nav: float                              # net asset value
    cash: float                             # available cash
    peak_equity: float                      # all-time high equity
    current_drawdown: float                 # fraction (0.0-1.0)
    positions: List = field(default_factory=list)
    daily_pnl: float = 0.0
    total_exposure: float = 0.0             # sum of all position notional values


@dataclass
class RiskResult:
    passed: bool
    sized_order: Optional[SizedOrder] = None
    failures: List[str] = field(default_factory=list)
    applied_size: float = 0.0
    applied_fraction: float = 0.0


@dataclass
class RiskConfig:
    min_confidence_threshold: float = float(os.getenv("MIN_CONFIDENCE_THRESHOLD", "0.55"))
    kelly_fraction: float = float(os.getenv("KELLY_FRACTION", "0.25"))
    max_position_pct: float = float(os.getenv("MAX_POSITION_PCT", "0.05"))
    max_total_exposure: float = float(os.getenv("MAX_TOTAL_EXPOSURE", "3.0"))
    max_drawdown: float = float(os.getenv("MAX_DRAWDOWN", "0.08"))
    daily_loss_limit: float = float(os.getenv("DAILY_LOSS_LIMIT", "5000"))
    daily_var_limit: float = float(os.getenv("DAILY_VAR_LIMIT", "10000"))
    slippage_threshold: float = float(os.getenv("SLIPPAGE_THRESHOLD", "0.02"))
    live_trading: bool = os.getenv("LIVE_TRADING", "false").lower() == "true"
    live_trading_confirm: bool = os.getenv("LIVE_TRADING_CONFIRM", "no").lower() == "yes"
    kill_switch_dir: str = os.getenv("KILL_SWITCH_DIR", "./flags")
