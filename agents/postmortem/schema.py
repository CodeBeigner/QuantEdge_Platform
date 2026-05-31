"""Post-Mortem Agent output schema."""
from __future__ import annotations

from dataclasses import dataclass, field
from typing import List


@dataclass
class PostMortemResult:
    trade_id: str
    asset: str
    outcome: str = ""
    classification: str = ""  # model_error, timing_error, execution_error, external_shock
    explanation: str = ""
    lessons: List[str] = field(default_factory=list)
    pnl: float = 0.0
