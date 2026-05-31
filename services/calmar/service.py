"""Calmar Ratio Engine — FastAPI service."""
from __future__ import annotations

import logging
from typing import Dict, List, Optional

import numpy as np
from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel

from services.calmar.engine import (
    compute_calmar,
    annualized_return,
    max_drawdown,
    calmar_benchmark,
    compute_strategy_calmar,
    compute_portfolio_calmar,
    rolling_calmar,
)
from services.calmar.diagnostics import trailing_stop_simulation, regime_filter_simulation

_log = logging.getLogger(__name__)

app = FastAPI(title="QuantEdge Calmar Engine", version="1.0.0")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["http://localhost:3000"],
    allow_methods=["*"],
    allow_headers=["*"],
)


class TradeLogEntry(BaseModel):
    date: str
    pnl: Optional[float] = None
    return_pct: float


class CalmarRequest(BaseModel):
    trade_log: List[TradeLogEntry]
    strategy_name: Optional[str] = None


class PortfolioCalmarRequest(BaseModel):
    strategies: Dict[str, List[TradeLogEntry]]
    weights: Optional[Dict[str, float]] = None


class DiagnosticRequest(BaseModel):
    trade_log: List[TradeLogEntry]
    regimes: Optional[List[str]] = None
    atr_multiplier: Optional[float] = 1.5


@app.get("/health")
async def health():
    return {"status": "UP", "service": "calmar-engine"}


@app.post("/calmar/strategy")
async def calmar_strategy(req: CalmarRequest):
    trades = [t.dict() for t in req.trade_log]
    result = compute_strategy_calmar(trades)
    if req.strategy_name:
        result["strategy"] = req.strategy_name
    return result


@app.post("/calmar/portfolio")
async def calmar_portfolio(req: PortfolioCalmarRequest):
    strategies = {name: [t.dict() for t in trades] for name, trades in req.strategies.items()}
    result = compute_portfolio_calmar(strategies, req.weights)
    return result


@app.post("/calmar/diagnostics/trailing-stop")
async def diag_trailing_stop(req: DiagnosticRequest):
    trades = [t.dict() for t in req.trade_log]
    return trailing_stop_simulation(trades, atr_multiplier=req.atr_multiplier or 1.5)


@app.post("/calmar/diagnostics/regime-filter")
async def diag_regime_filter(req: DiagnosticRequest):
    if not req.regimes:
        raise HTTPException(status_code=400, detail="regimes list required for regime filter simulation")
    trades = [t.dict() for t in req.trade_log]
    return regime_filter_simulation(trades, req.regimes)


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=5005)
