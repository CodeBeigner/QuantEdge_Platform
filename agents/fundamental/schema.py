"""Fundamental Analyst output schema."""
from __future__ import annotations

from dataclasses import dataclass, field
from typing import List, Optional


@dataclass
class ValuationRange:
    bear: float
    base: float
    bull: float


@dataclass
class CompData:
    ticker: str
    name: str
    ev_ebitda: Optional[float] = None
    ev_revenue: Optional[float] = None
    pe_ratio: Optional[float] = None
    market_cap: float = 0.0


@dataclass
class FundamentalReport:
    symbol: str
    company_name: str = ""
    current_price: float = 0.0
    implied_price: ValuationRange = field(default_factory=lambda: ValuationRange(0, 0, 0))
    upside_pct: float = 0.0
    wacc_range: str = "10-12%"
    comps: List[CompData] = field(default_factory=list)
    dcf_revenue_growth: float = 0.0
    summary: str = ""
    recommendation: str = "HOLD"
    confidence: float = 0.0
    timestamp: str = ""
