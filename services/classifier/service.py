"""Strategy Classifier — FastAPI service."""
from __future__ import annotations

import logging
from datetime import datetime
from typing import Dict, List, Optional

import numpy as np
from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel

from services.classifier.strategy_style import (
    StrategyStyle, AssetClass, StrategyRegistration, SignalSuppression,
)
from services.classifier.regime_guard import classify_regime, regime_transition_risk, is_adverse_regime
from services.classifier.signal_policy import enforce_policy, validate_registration, allocation_cap

_log = logging.getLogger(__name__)

app = FastAPI(title="QuantEdge Strategy Classifier", version="1.0.0")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["http://localhost:3000"],
    allow_methods=["*"],
    allow_headers=["*"],
)

_registry: Dict[str, StrategyRegistration] = {}


class RegisterRequest(BaseModel):
    name: str
    style: str
    asset_class: str
    max_allocation_pct: float = 15.0
    survivorship_bias_corrected: bool = False
    regime_attached: bool = False
    correlated_assets: Optional[List[str]] = None
    hft_override_acknowledged: bool = False


class SignalCheckRequest(BaseModel):
    strategy_name: str
    returns: List[float]
    correlation: Optional[float] = None


class RegimeRequest(BaseModel):
    returns: List[float]


@app.get("/health")
async def health():
    return {"status": "UP", "service": "classifier"}


@app.post("/classifier/register")
async def register_strategy(req: RegisterRequest):
    if req.name in _registry:
        raise HTTPException(status_code=409, detail=f"Strategy '{req.name}' already registered")

    reg = StrategyRegistration(
        name=req.name,
        style=StrategyStyle(req.style),
        asset_class=AssetClass(req.asset_class),
        max_allocation_pct=req.max_allocation_pct,
        survivorship_bias_corrected=req.survivorship_bias_corrected,
        regime_attached=req.regime_attached,
        correlated_assets=req.correlated_assets,
        hft_override_acknowledged=req.hft_override_acknowledged,
        created_at=datetime.utcnow().isoformat(),
    )

    warnings = validate_registration(reg)
    _registry[req.name] = reg

    return {
        "status": "registered",
        "strategy": req.name,
        "style": req.style,
        "warnings": warnings,
    }


@app.get("/classifier/list")
async def list_strategies():
    return {
        "strategies": [
            {
                "name": r.name,
                "style": r.style.value,
                "asset_class": r.asset_class.value,
                "max_allocation_pct": r.max_allocation_pct,
                "regime_attached": r.regime_attached,
            }
            for r in _registry.values()
        ],
        "count": len(_registry),
    }


@app.post("/classifier/check-signal")
async def check_signal(req: SignalCheckRequest):
    if req.strategy_name not in _registry:
        raise HTTPException(status_code=404, detail=f"Strategy '{req.strategy_name}' not registered")

    reg = _registry[req.strategy_name]
    returns = np.array(req.returns)
    regime, model = classify_regime(returns)
    transition_risk = regime_transition_risk(returns)

    result = enforce_policy(reg, regime, req.correlation)

    return {
        "strategy": req.strategy_name,
        "style": reg.style.value,
        "current_regime": regime,
        "transition_risk": transition_risk,
        "suppressed": result.suppressed,
        "reason": result.reason,
        "policy": result.policy,
    }


@app.post("/classifier/regime")
async def get_regime(req: RegimeRequest):
    returns = np.array(req.returns)
    if len(returns) < 30:
        raise HTTPException(status_code=422, detail=f"Need at least 30 data points, got {len(returns)}")

    regime, model = classify_regime(returns)
    transition_risk = regime_transition_risk(returns)

    return {
        "regime": regime,
        "transition_risk": transition_risk,
        "data_points": len(returns),
    }


@app.get("/classifier/styles")
async def list_styles():
    return {"styles": [s.value for s in StrategyStyle]}


@app.get("/classifier/asset-classes")
async def list_asset_classes():
    return {"asset_classes": [a.value for a in AssetClass]}


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=5006)
