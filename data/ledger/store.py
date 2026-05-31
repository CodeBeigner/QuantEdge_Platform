"""Append-only Trade Ledger — writes TradeRecords as JSON lines."""
from __future__ import annotations

import json
import logging
import os
from dataclasses import asdict
from pathlib import Path
from typing import List, Optional

from data.ledger.schema import TradeRecord

_log = logging.getLogger(__name__)


class TradeLedger:
    def __init__(self, ledger_path: Optional[str] = None):
        self.ledger_path = Path(ledger_path or os.getenv("LEDGER_PATH", "data/ledger/trades.jsonl"))
        self.ledger_path.parent.mkdir(parents=True, exist_ok=True)

    def log(self, record: TradeRecord) -> None:
        with open(self.ledger_path, "a") as f:
            f.write(json.dumps(asdict(record), default=str) + "\n")
        _log.info("Trade logged: %s %s %s $%.2f P&L", record.trade_id, record.asset, record.direction, record.pnl)

    def read_all(self) -> List[dict]:
        if not self.ledger_path.exists():
            return []
        records = []
        with open(self.ledger_path, "r") as f:
            for line in f:
                line = line.strip()
                if line:
                    try:
                        records.append(json.loads(line))
                    except json.JSONDecodeError:
                        continue
        return records

    def get_by_symbol(self, symbol: str) -> List[dict]:
        return [r for r in self.read_all() if r.get("asset") == symbol]

    def get_by_outcome(self, outcome: str) -> List[dict]:
        return [r for r in self.read_all() if r.get("outcome") == outcome]

    def stats(self) -> dict:
        records = self.read_all()
        if not records:
            return {"total_trades": 0, "win_rate": 0.0, "total_pnl": 0.0, "avg_pnl": 0.0}

        wins = [r for r in records if r.get("outcome") == "WIN"]
        losses = [r for r in records if r.get("outcome") == "LOSS"]
        total_pnl = sum(r.get("pnl", 0) for r in records)
        win_rate = len(wins) / len(records) if records else 0.0

        return {
            "total_trades": len(records),
            "wins": len(wins),
            "losses": len(losses),
            "win_rate": round(win_rate, 4),
            "total_pnl": round(total_pnl, 2),
            "avg_pnl": round(total_pnl / len(records), 2) if records else 0.0,
        }
