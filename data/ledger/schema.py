"""Trade Ledger — append-only, versioned TradeRecord schema."""
from __future__ import annotations

from dataclasses import dataclass, field, asdict
from datetime import datetime
from typing import Dict, Optional


@dataclass
class TradeRecord:
    trade_id: str
    asset: str
    direction: str  # LONG, SHORT
    entry_price: float
    exit_price: float = 0.0
    size_dollars: float = 0.0
    quantity: float = 0.0
    pnl: float = 0.0
    pnl_pct: float = 0.0
    model_probability: float = 0.5
    confidence: float = 0.0
    time_held_hours: float = 0.0
    entry_timestamp: str = ""
    exit_timestamp: str = ""
    market_conditions: Dict[str, str] = field(default_factory=dict)
    outcome: str = "OPEN"  # OPEN, WIN, LOSS, BREAKEVEN
    outcome_class: str = ""  # model_error, timing_error, execution_error, external_shock
    rationale: str = ""
    schema_version: str = "1.0.0"

    def close(self, exit_price: float, exit_timestamp: Optional[str] = None):
        self.exit_price = exit_price
        self.exit_timestamp = exit_timestamp or datetime.utcnow().isoformat()
        if self.entry_price > 0:
            price_change = (exit_price - self.entry_price) / self.entry_price
            if self.direction == "SHORT":
                price_change = -price_change
            self.pnl = self.size_dollars * price_change
            self.pnl_pct = price_change * 100

        if self.entry_timestamp and self.exit_timestamp:
            entry = datetime.fromisoformat(self.entry_timestamp)
            exit_t = datetime.fromisoformat(self.exit_timestamp)
            self.time_held_hours = (exit_t - entry).total_seconds() / 3600

        if self.pnl > 1:
            self.outcome = "WIN"
        elif self.pnl < -1:
            self.outcome = "LOSS"
        else:
            self.outcome = "BREAKEVEN"
