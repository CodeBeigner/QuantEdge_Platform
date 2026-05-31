"""Kronos FastAPI service — forecasting endpoints."""
from __future__ import annotations

import os
from typing import List, Optional

import numpy as np
import pandas as pd
from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel

from services.kronos.predictor import KronosPredictor

app = FastAPI(title="QuantEdge Kronos Service", version="1.0.0")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["http://localhost:3000"],
    allow_methods=["*"],
    allow_headers=["*"],
)

_predictor: Optional[KronosPredictor] = None


def get_predictor() -> KronosPredictor:
    global _predictor
    if _predictor is None:
        _predictor = KronosPredictor()
    return _predictor


class ForecastRequest(BaseModel):
    symbol: str
    ohlcv: List[List[float]]  # [[o,h,l,c,v], ...]
    timestamps: Optional[List[str]] = None
    pred_len: Optional[int] = None
    sample_count: Optional[int] = None


class BatchForecastRequest(BaseModel):
    symbols: List[str]
    ohlcv_list: List[List[List[float]]]
    pred_len: Optional[int] = None


@app.get("/health")
async def health():
    p = get_predictor()
    return {
        "status": "UP",
        "service": "kronos",
        "model_loaded": p.is_loaded,
        **p.status,
    }


@app.post("/forecast/{symbol}")
async def forecast(symbol: str, req: ForecastRequest):
    p = get_predictor()
    if not p.is_loaded:
        raise HTTPException(status_code=503, detail="Kronos model not loaded")

    df = pd.DataFrame(req.ohlcv, columns=["open", "high", "low", "close", "volume"])
    timestamps = pd.to_datetime(req.timestamps) if req.timestamps else None

    result = p.forecast(df, x_timestamp=timestamps, pred_len=req.pred_len, sample_count=req.sample_count)
    result["symbol"] = symbol

    if result.get("status") == "error":
        raise HTTPException(status_code=500, detail=result["message"])

    return result


@app.post("/batch-forecast")
async def batch_forecast(req: BatchForecastRequest):
    p = get_predictor()
    if not p.is_loaded:
        raise HTTPException(status_code=503, detail="Kronos model not loaded")

    dfs = []
    for ohlcv in req.ohlcv_list:
        df = pd.DataFrame(ohlcv, columns=["open", "high", "low", "close", "volume"])
        dfs.append(df)

    results = p.forecast_batch(dfs, pred_len=req.pred_len)
    for i, r in enumerate(results):
        if i < len(req.symbols):
            r["symbol"] = req.symbols[i]

    return {"status": "ok", "results": results}


if __name__ == "__main__":
    import uvicorn
    port = int(os.getenv("KRONOS_PORT", "5003"))
    uvicorn.run(app, host="0.0.0.0", port=port)
