"""
QuantEdge ML Microservice — FastAPI

Endpoints:
  POST /train/{symbol}     — Train XGBoost model on historical data
  POST /predict/{symbol}   — Get ML signal (BUY/SELL/HOLD)
  GET  /features/{symbol}  — Get computed technical indicators
  POST /optimize           — Portfolio optimization
  GET  /health             — Service health check
"""
import httpx
import numpy as np
import pandas as pd
from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
from typing import List, Optional

from feature_engine import compute_features, FEATURE_COLS
from model import SignalModel
from optimizer import optimize_portfolio, compute_efficient_frontier

app = FastAPI(title="QuantEdge ML Service", version="1.0.0")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

# Global model instances per symbol
models: dict[str, SignalModel] = {}

BACKEND_URL = "http://localhost:8080/api/v1"


async def fetch_market_data(symbol: str, days: int = 500) -> pd.DataFrame:
    """Fetch OHLCV data from the Java backend."""
    async with httpx.AsyncClient() as client:
        try:
            res = await client.get(
                f"{BACKEND_URL}/market-data/prices/{symbol}?days={days}",
                timeout=10.0,
            )
            if res.status_code != 200:
                raise HTTPException(status_code=502, detail=f"Backend returned {res.status_code}")
            data = res.json()
            if not data:
                raise HTTPException(status_code=404, detail=f"No data for {symbol}")
            return pd.DataFrame(data)
        except httpx.ConnectError:
            raise HTTPException(status_code=503, detail="Cannot connect to backend")


# ── Request/Response Models ──────────────────────────────────

class OptimizeRequest(BaseModel):
    symbols: List[str]
    days: int = 252
    risk_free_rate: float = 0.02
    target_return: Optional[float] = None


# ── Endpoints ────────────────────────────────────────────────

@app.get("/health")
async def health():
    return {
        "status": "UP",
        "service": "ml-service",
        "models_loaded": list(models.keys()),
        "features_available": FEATURE_COLS,
    }


@app.post("/train/{symbol}")
async def train_model(symbol: str, days: int = 500):
    """Train XGBoost model on historical data for a symbol."""
    df = await fetch_market_data(symbol, days)
    model = SignalModel()
    result = model.train(df)
    if "error" in result:
        raise HTTPException(status_code=400, detail=result["error"])
    models[symbol] = model
    return result


@app.post("/predict/{symbol}")
async def predict_signal(symbol: str, days: int = 500):
    """Get ML signal prediction for a symbol."""
    if symbol not in models:
        # Auto-train if not trained yet
        df = await fetch_market_data(symbol, days)
        model = SignalModel()
        train_result = model.train(df)
        if "error" in train_result:
            raise HTTPException(status_code=400, detail=train_result["error"])
        models[symbol] = model

    df = await fetch_market_data(symbol, 100)
    return models[symbol].predict(df)


@app.get("/features/{symbol}")
async def get_features(symbol: str, days: int = 100):
    """Get computed technical indicators for a symbol."""
    df = await fetch_market_data(symbol, days)
    featured = compute_features(df)
    featured = featured.dropna(subset=FEATURE_COLS)

    # Return last 20 rows of features
    result = featured[['close'] + FEATURE_COLS].tail(20)
    return result.round(4).to_dict(orient='records')


@app.post("/optimize")
async def optimize(req: OptimizeRequest):
    """Run Markowitz portfolio optimization across symbols."""
    all_returns = []
    valid_symbols = []

    for symbol in req.symbols:
        try:
            df = await fetch_market_data(symbol, req.days)
            close = pd.to_numeric(df['close'], errors='coerce')
            rets = close.pct_change().dropna().values
            all_returns.append(rets)
            valid_symbols.append(symbol)
        except Exception:
            continue

    if len(valid_symbols) < 2:
        raise HTTPException(status_code=400, detail="Need at least 2 symbols with data")

    # Align to shortest length
    min_len = min(len(r) for r in all_returns)
    returns = np.column_stack([r[-min_len:] for r in all_returns])

    result = optimize_portfolio(
        returns, valid_symbols,
        risk_free_rate=req.risk_free_rate,
        target_return=req.target_return,
    )

    frontier = compute_efficient_frontier(returns, valid_symbols)
    result["efficient_frontier"] = frontier

    return result


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=5001)
