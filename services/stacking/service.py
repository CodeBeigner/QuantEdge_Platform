"""Portfolio Stacking Engine — FastAPI service."""
from __future__ import annotations

import logging
from typing import Dict, List, Optional

import numpy as np
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel

from services.stacking.correlation import pair_matrix, correlation_heatmap_data, detect_concentration_risk
from services.stacking.allocation import stacking_optimizer, compute_portfolio_sharpe
from services.stacking.equity_curve import combined_equity_curve

_log = logging.getLogger(__name__)

app = FastAPI(title="QuantEdge Portfolio Stacking", version="1.0.0")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["http://localhost:3000"],
    allow_methods=["*"],
    allow_headers=["*"],
)


class StackingRequest(BaseModel):
    returns: Dict[str, List[float]]
    weights: Optional[Dict[str, float]] = None
    max_per_strategy: float = 0.15


@app.get("/health")
async def health():
    return {"status": "UP", "service": "stacking-engine"}


@app.post("/stacking/correlation")
async def get_correlation(req: StackingRequest):
    return pair_matrix(req.returns)


@app.post("/stacking/correlation/heatmap")
async def get_correlation_heatmap(req: StackingRequest):
    data = correlation_heatmap_data(req.returns)
    matrix = pair_matrix(req.returns)
    return {"data": data, "labels": matrix["labels"], "flagged_pairs": matrix["flagged_pairs"]}


@app.post("/stacking/optimize")
async def optimize_allocation(req: StackingRequest):
    returns_np = {k: np.array(v) for k, v in req.returns.items()}
    result = stacking_optimizer(returns_np, max_per_strategy=req.max_per_strategy)

    alloc_dict = {name: round(w * 100, 1) for name, w in result["weights"].items()}
    concentration_warnings = detect_concentration_risk(alloc_dict)
    result["concentration_warnings"] = concentration_warnings

    return result


@app.post("/stacking/sharpe")
async def get_portfolio_sharpe(req: StackingRequest):
    returns_np = {k: np.array(v) for k, v in req.returns.items()}
    if req.weights:
        sharpe = compute_portfolio_sharpe(returns_np, req.weights)
        return {"portfolio_sharpe": round(sharpe, 4)}

    equal = {k: 1.0 / len(req.returns) for k in req.returns}
    sharpe = compute_portfolio_sharpe(returns_np, equal)
    return {"portfolio_sharpe": round(sharpe, 4), "weights_used": "equal", "note": "No weights provided — using equal allocation"}


@app.post("/stacking/equity-curve")
async def get_equity_curve(req: StackingRequest):
    if not req.weights:
        req.weights = {k: 1.0 / len(req.returns) for k in req.returns}

    result = combined_equity_curve(req.returns, req.weights)
    return result


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=5008)
