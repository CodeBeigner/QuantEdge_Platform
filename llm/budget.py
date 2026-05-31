"""Per-agent LLM token budget tracking."""
from __future__ import annotations

import logging
from dataclasses import dataclass, field
from datetime import date
from typing import Dict, Optional

from llm.config import LLMConfig

_log = logging.getLogger(__name__)


@dataclass
class AgentBudgetState:
    agent: str
    daily_limit_usd: float
    spent_today_usd: float = 0.0
    input_tokens_today: int = 0
    output_tokens_today: int = 0
    calls_today: int = 0
    last_date: str = ""


class LLMBudget:
    def __init__(self, config: Optional[LLMConfig] = None):
        self.config = config or LLMConfig()
        self._states: Dict[str, AgentBudgetState] = {}
        self._total_spent: float = 0.0
        self._today = date.today().isoformat()

    def _check_date(self):
        today = date.today().isoformat()
        if today != self._today:
            self._today = today
            for state in self._states.values():
                state.spent_today_usd = 0.0
                state.input_tokens_today = 0
                state.output_tokens_today = 0
                state.calls_today = 0
            self._total_spent = 0.0

    def can_call(self, agent: str) -> bool:
        self._check_date()
        if self._total_spent >= self.config.daily_budget_usd:
            return False
        if agent in self._states:
            limit = self.config.agent_budgets.get(agent, self.config.daily_budget_usd)
            return self._states[agent].spent_today_usd < limit
        return True

    def record_call(
        self,
        agent: str,
        input_tokens: int,
        output_tokens: int,
        cost_usd: float,
    ):
        self._check_date()
        if agent not in self._states:
            limit = self.config.agent_budgets.get(agent, self.config.daily_budget_usd)
            self._states[agent] = AgentBudgetState(agent=agent, daily_limit_usd=limit, last_date=self._today)
        state = self._states[agent]
        state.spent_today_usd += cost_usd
        state.input_tokens_today += input_tokens
        state.output_tokens_today += output_tokens
        state.calls_today += 1
        state.last_date = self._today
        self._total_spent += cost_usd

    def get_status(self) -> dict:
        self._check_date()
        return {
            "date": self._today,
            "total_spent_usd": round(self._total_spent, 4),
            "daily_budget_usd": self.config.daily_budget_usd,
            "remaining_usd": round(self.config.daily_budget_usd - self._total_spent, 4),
            "agents": {
                name: {
                    "spent": round(s.spent_today_usd, 4),
                    "limit": s.daily_limit_usd,
                    "calls": s.calls_today,
                    "input_tokens": s.input_tokens_today,
                    "output_tokens": s.output_tokens_today,
                }
                for name, s in self._states.items()
            },
        }
