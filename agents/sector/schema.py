"""Sector & Macro Agent output schema."""
from __future__ import annotations

from dataclasses import dataclass, field
from typing import List, Optional


@dataclass
class MacroRegimeReport:
    regime: str = "NEUTRAL"  # RISK_ON, RISK_OFF, NEUTRAL
    vix_level: str = "NORMAL"  # LOW, NORMAL, ELEVATED, EXTREME
    rate_environment: str = "STABLE"  # RISING, FALLING, STABLE
    credit_spreads: str = "NORMAL"  # TIGHT, NORMAL, WIDE
    sector_rotation: List[dict] = field(default_factory=list)
    breadth_summary: str = ""
    implications: str = ""
    timestamp: str = ""
