"""Earnings/Catalyst Agent output schema."""
from __future__ import annotations

from dataclasses import dataclass, field
from typing import List, Optional


@dataclass
class EarningsSignal:
    symbol: str
    event_type: str = "earnings"  # earnings, fed_decision, macro_release
    event_date: str = ""
    verdict: str = "NEUTRAL"  # UPGRADE, DOWNGRADE, NEUTRAL
    beats_misses: str = ""  # e.g., "Beat EPS by 5%, Missed Revenue by 2%"
    guidance_change: str = ""  # e.g., "Raised FY guidance"
    tone: str = "NEUTRAL"  # BULLISH, BEARISH, NEUTRAL
    key_points: List[str] = field(default_factory=list)
    confidence: float = 0.0
    summary: str = ""
    timestamp: str = ""
