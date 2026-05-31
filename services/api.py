"""Pillar C Risk REST API — exposes risk engine state to the frontend."""
from __future__ import annotations

import os
from pathlib import Path
from typing import Dict, List, Optional

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from services.risk.config import RiskConfig
from services.risk.kill_switch import KillSwitch
from services.execution.paper import PaperProvider
from services.data.yfinance import YFinanceProvider
from agents.scanner.scanner import MarketScanner

app = FastAPI(title="QuantEdge Risk API", version="1.0.0")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["http://localhost:3000"],
    allow_methods=["*"],
    allow_headers=["*"],
)

_config = RiskConfig()
_kill_switch = KillSwitch(flag_dir=_config.kill_switch_dir)
_paper = PaperProvider(initial_cash=float(os.getenv("PAPER_INITIAL_BALANCE", "500.0")))

_signal_history: List[dict] = []

_scanner: Optional[MarketScanner] = None

def _get_scanner() -> MarketScanner:
    global _scanner
    if _scanner is None:
        provider = YFinanceProvider()
        _scanner = MarketScanner(provider=provider)
    return _scanner


@app.get("/api/risk/status")
async def risk_status():
    return {
        "kill_switch_active": _kill_switch.is_active(),
        "kill_switch_dir": str(_kill_switch.flag_dir),
        "kill_switch_healthy": _kill_switch.health_check(),
        "live_trading": _config.live_trading,
        "config": {
            "min_confidence": _config.min_confidence_threshold,
            "kelly_fraction": _config.kelly_fraction,
            "max_position_pct": _config.max_position_pct,
            "max_total_exposure": _config.max_total_exposure,
            "max_drawdown": _config.max_drawdown,
            "daily_loss_limit": _config.daily_loss_limit,
            "daily_var_limit": _config.daily_var_limit,
            "slippage_threshold": _config.slippage_threshold,
        },
    }


@app.get("/api/risk/portfolio")
async def risk_portfolio():
    account = await _paper.get_account()
    positions = await _paper.get_positions()
    return {
        "cash": account.cash,
        "equity": account.equity,
        "buying_power": account.buying_power,
        "total_exposure": sum(p.size * p.current_price for p in positions),
        "position_count": len(positions),
        "positions": [
            {
                "asset": p.asset,
                "size": p.size,
                "entry_price": p.entry_price,
                "current_price": p.current_price,
                "unrealized_pnl": p.unrealized_pnl,
            }
            for p in positions
        ],
    }


@app.get("/api/risk/signals")
async def risk_signals():
    return {
        "signals": _signal_history[-20:],
        "total_signals": len(_signal_history),
    }


@app.get("/health")
async def health():
    return {"status": "UP", "service": "risk-api"}


@app.get("/api/risk/opportunities")
async def risk_opportunities():
    scanner = _get_scanner()
    result = scanner.scan()
    return {
        "scanned": result.scanned_count,
        "filtered": result.filtered_count,
        "timestamp": result.timestamp,
        "opportunities": [
            {
                "asset": o.asset,
                "asset_type": o.asset_type,
                "price": o.price,
                "change_24h_pct": o.change_24h_pct,
                "volume_24h": o.volume_24h,
                "signal_strength": o.signal_strength,
                "reasons": o.reasons,
            }
            for o in result.top(10)
        ],
    }


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=5002)
