"""Brier Score calibration tracking per agent."""
from __future__ import annotations

from dataclasses import dataclass, field
from typing import Dict, List, Optional


@dataclass
class CalibrationRecord:
    agent: str
    asset: str
    predicted_prob: float
    actual_outcome: int  # 1 = correct direction, 0 = wrong
    timestamp: str = ""


class CalibrationTracker:
    def __init__(self):
        self._records: List[CalibrationRecord] = []

    def record(self, agent: str, asset: str, predicted_prob: float, actual_outcome: int):
        self._records.append(CalibrationRecord(
            agent=agent,
            asset=asset,
            predicted_prob=predicted_prob,
            actual_outcome=actual_outcome,
        ))

    def brier_score(self, agent: Optional[str] = None) -> float:
        records = self._records if agent is None else [r for r in self._records if r.agent == agent]
        if not records:
            return 0.0
        return sum((r.predicted_prob - r.actual_outcome) ** 2 for r in records) / len(records)

    def get_agent_scores(self) -> Dict[str, float]:
        agents = set(r.agent for r in self._records)
        return {a: self.brier_score(a) for a in agents}

    def count(self, agent: Optional[str] = None) -> int:
        if agent is None:
            return len(self._records)
        return len([r for r in self._records if r.agent == agent])
