"""
QuantEdge ML Microservice — FastAPI

Endpoints:
  POST /train/{symbol}              — Train XGBoost model on historical data
  POST /predict/{symbol}            — Get XGBoost ML signal (BUY/SELL/HOLD)
  GET  /features/{symbol}           — Get computed technical indicators
  POST /optimize                    — Portfolio optimization (Markowitz)
  POST /optimize-robust             — Portfolio optimization (Ledoit-Wolf)
  POST /risk-parity                 — Risk parity allocation
  POST /train-lstm/{symbol}         — Train LSTM model on historical data
  POST /predict-lstm/{symbol}       — Get LSTM ML signal
  POST /predict-ensemble/{symbol}   — Get ensemble prediction (XGBoost + LSTM)
  POST /save-model/{symbol}         — Save trained model to disk
  POST /load-model/{symbol}         — Load model from disk
  POST /walk-forward/{symbol}       — Walk-forward validation
  GET  /model-info/{symbol}         — Model metadata
  GET  /ic/{symbol}                 — Compute Information Coefficient
  GET  /health                      — Service health check
"""
import glob
import logging
import os

import httpx
import numpy as np
import pandas as pd
from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
from typing import List, Optional
from scipy import stats

from feature_engine import compute_features, FEATURE_COLS
from model import SignalModel, LSTMSignalModel
from optimizer import (
    optimize_portfolio,
    optimize_portfolio_robust,
    risk_parity_allocation,
    compute_efficient_frontier,
)

log = logging.getLogger("ml-service")

app = FastAPI(title="QuantEdge ML Service", version="2.0.0")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

# Global model instances per symbol
models: dict[str, SignalModel] = {}
lstm_models: dict[str, LSTMSignalModel] = {}

MODEL_DIR = "models/"
BACKEND_URL = "http://localhost:8080/api/v1"


@app.on_event("startup")
async def load_saved_models():
    """Try to load all previously saved models from the models/ directory."""
    if not os.path.isdir(MODEL_DIR):
        return

    for path in glob.glob(os.path.join(MODEL_DIR, "*_xgb.joblib")):
        symbol = os.path.basename(path).replace("_xgb.joblib", "")
        try:
            m = SignalModel()
            result = m.load_model(symbol, MODEL_DIR)
            if "error" not in result:
                models[symbol] = m
                log.info("Loaded XGBoost model for %s", symbol)
        except Exception as exc:
            log.warning("Failed to load XGBoost model for %s: %s", symbol, exc)

    for path in glob.glob(os.path.join(MODEL_DIR, "*_lstm.pt")):
        symbol = os.path.basename(path).replace("_lstm.pt", "")
        try:
            m = LSTMSignalModel()
            result = m.load_model(symbol, MODEL_DIR)
            if "error" not in result:
                lstm_models[symbol] = m
                log.info("Loaded LSTM model for %s", symbol)
        except Exception as exc:
            log.warning("Failed to load LSTM model for %s: %s", symbol, exc)

ENSEMBLE_XGBOOST_WEIGHT = 0.6
ENSEMBLE_LSTM_WEIGHT = 0.4
IC_DEFAULT_WINDOW = 60


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
        "version": "2.0.0",
        "xgboost_models": list(models.keys()),
        "lstm_models": list(lstm_models.keys()),
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
    """Get XGBoost ML signal prediction for a symbol."""
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


# ── Robust Optimization & Risk Parity ────────────────────────

@app.post("/optimize-robust")
async def optimize_robust(req: OptimizeRequest):
    """Portfolio optimization with Ledoit-Wolf shrinkage covariance."""
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

    min_len = min(len(r) for r in all_returns)
    returns = np.column_stack([r[-min_len:] for r in all_returns])

    return optimize_portfolio_robust(
        returns, valid_symbols,
        risk_free_rate=req.risk_free_rate,
        target_return=req.target_return,
    )


@app.post("/risk-parity")
async def risk_parity(req: OptimizeRequest):
    """Risk parity allocation across symbols."""
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

    min_len = min(len(r) for r in all_returns)
    returns = np.column_stack([r[-min_len:] for r in all_returns])

    return risk_parity_allocation(returns, valid_symbols)


# ── Model Persistence & Validation ──────────────────────────

@app.post("/save-model/{symbol}")
async def save_model(symbol: str):
    """Save trained model(s) to disk."""
    results = {}
    if symbol in models:
        results["xgboost"] = models[symbol].save_model(symbol, MODEL_DIR)
    if symbol in lstm_models:
        results["lstm"] = lstm_models[symbol].save_model(symbol, MODEL_DIR)
    if not results:
        raise HTTPException(status_code=404, detail=f"No trained models for {symbol}")
    return results


@app.post("/load-model/{symbol}")
async def load_model(symbol: str):
    """Load model(s) from disk."""
    results = {}
    xgb_m = SignalModel()
    xgb_result = xgb_m.load_model(symbol, MODEL_DIR)
    if "error" not in xgb_result:
        models[symbol] = xgb_m
        results["xgboost"] = xgb_result

    lstm_m = LSTMSignalModel()
    lstm_result = lstm_m.load_model(symbol, MODEL_DIR)
    if "error" not in lstm_result:
        lstm_models[symbol] = lstm_m
        results["lstm"] = lstm_result

    if not results:
        raise HTTPException(status_code=404, detail=f"No saved models found for {symbol}")
    return results


@app.post("/walk-forward/{symbol}")
async def walk_forward(symbol: str, days: int = 1000, n_splits: int = 5):
    """Run walk-forward validation for XGBoost model."""
    df = await fetch_market_data(symbol, days)
    model = SignalModel()
    result = model.walk_forward_validate(df, n_splits=n_splits)
    if "error" in result:
        raise HTTPException(status_code=400, detail=result["error"])
    return result


@app.get("/model-info/{symbol}")
async def model_info(symbol: str):
    """Return metadata about trained models for a symbol."""
    info = {"symbol": symbol, "models": {}}

    if symbol in models:
        m = models[symbol]
        info["models"]["xgboost"] = {
            "is_trained": m.is_trained,
            "accuracy": m.accuracy,
            "trained_date": m.trained_date,
            "features": FEATURE_COLS,
        }

    if symbol in lstm_models:
        m = lstm_models[symbol]
        info["models"]["lstm"] = {
            "is_trained": m.is_trained,
            "accuracy": m.accuracy,
            "trained_date": m.trained_date,
        }

    if not info["models"]:
        raise HTTPException(status_code=404, detail=f"No trained models for {symbol}")
    return info


# ── LSTM Endpoints ───────────────────────────────────────────

@app.post("/train-lstm/{symbol}")
async def train_lstm(symbol: str, days: int = 500):
    """Train LSTM model on historical data for a symbol."""
    df = await fetch_market_data(symbol, days)
    model = LSTMSignalModel()
    result = model.train(df)
    if "error" in result:
        raise HTTPException(status_code=400, detail=result["error"])
    lstm_models[symbol] = model
    return result


@app.post("/predict-lstm/{symbol}")
async def predict_lstm(symbol: str, days: int = 500):
    """Get LSTM ML signal prediction for a symbol."""
    if symbol not in lstm_models:
        df = await fetch_market_data(symbol, days)
        model = LSTMSignalModel()
        train_result = model.train(df)
        if "error" in train_result:
            raise HTTPException(status_code=400, detail=train_result["error"])
        lstm_models[symbol] = model

    df = await fetch_market_data(symbol, 100)
    return lstm_models[symbol].predict(df)


# ── Ensemble Prediction ─────────────────────────────────────

@app.post("/predict-ensemble/{symbol}")
async def predict_ensemble(symbol: str, days: int = 500):
    """Get ensemble prediction combining XGBoost and LSTM signals.

    Uses weighted average: 60% XGBoost + 40% LSTM.
    Falls back to single model if only one is available.
    """
    xgb_result = None
    lstm_result = None

    # Try XGBoost prediction
    if symbol in models:
        df = await fetch_market_data(symbol, 100)
        xgb_result = models[symbol].predict(df)
        if "error" in xgb_result:
            xgb_result = None

    # Try LSTM prediction
    if symbol in lstm_models:
        df = await fetch_market_data(symbol, 100)
        lstm_result = lstm_models[symbol].predict(df)
        if "error" in lstm_result:
            lstm_result = None

    if xgb_result is None and lstm_result is None:
        raise HTTPException(status_code=400, detail=f"No trained models for {symbol}. Train XGBoost or LSTM first.")

    # Single model fallback
    if xgb_result is None:
        lstm_result["ensemble"] = False
        lstm_result["model_used"] = "LSTM_ONLY"
        return lstm_result

    if lstm_result is None:
        xgb_result["ensemble"] = False
        xgb_result["model_used"] = "XGBOOST_ONLY"
        return xgb_result

    # Weighted ensemble
    xgb_up = xgb_result["direction_prob"]["up"]
    xgb_down = xgb_result["direction_prob"]["down"]
    lstm_up = lstm_result["direction_prob"]["up"]
    lstm_down = lstm_result["direction_prob"]["down"]

    ensemble_up = ENSEMBLE_XGBOOST_WEIGHT * xgb_up + ENSEMBLE_LSTM_WEIGHT * lstm_up
    ensemble_down = ENSEMBLE_XGBOOST_WEIGHT * xgb_down + ENSEMBLE_LSTM_WEIGHT * lstm_down

    confidence = max(ensemble_up, ensemble_down)
    if confidence < 0.55:
        signal = "HOLD"
    elif ensemble_up > ensemble_down:
        signal = "BUY"
    else:
        signal = "SELL"

    return {
        "signal": signal,
        "confidence": round(confidence, 4),
        "direction_prob": {"up": round(ensemble_up, 4), "down": round(ensemble_down, 4)},
        "ensemble": True,
        "model_used": "XGBOOST+LSTM",
        "weights": {"xgboost": ENSEMBLE_XGBOOST_WEIGHT, "lstm": ENSEMBLE_LSTM_WEIGHT},
        "xgboost_signal": xgb_result["signal"],
        "lstm_signal": lstm_result["signal"],
        "xgboost_accuracy": xgb_result.get("model_accuracy", 0),
        "lstm_accuracy": lstm_result.get("model_accuracy", 0),
    }


# ── Information Coefficient ──────────────────────────────────

@app.get("/ic/{symbol}")
async def compute_ic(symbol: str, days: int = IC_DEFAULT_WINDOW):
    """Compute Information Coefficient for a symbol's XGBoost model.

    IC = Spearman rank correlation between predicted probabilities
    and actual next-day returns over the specified window.
    """
    if symbol not in models:
        raise HTTPException(status_code=400, detail=f"No trained model for {symbol}")

    df = await fetch_market_data(symbol, days + 50)
    featured = compute_features(df)
    featured = featured.dropna(subset=FEATURE_COLS)

    if len(featured) < days:
        raise HTTPException(status_code=400, detail=f"Not enough data for IC computation")

    model = models[symbol]
    predictions = []
    actuals = []

    # Compute rolling predictions vs actuals
    for i in range(len(featured) - days, len(featured) - 1):
        row = featured.iloc[i]
        X = row[FEATURE_COLS].values.reshape(1, -1)
        proba = model.model.predict_proba(X)[0]
        predicted_return = proba[1] - proba[0]  # net directional signal
        predictions.append(predicted_return)

        # Actual next-day return
        actual_return = (featured.iloc[i + 1]['close'] - row['close']) / row['close']
        actuals.append(actual_return)

    if len(predictions) < 10:
        raise HTTPException(status_code=400, detail="Not enough predictions for IC")

    # Spearman rank correlation
    ic, p_value = stats.spearmanr(predictions, actuals)

    return {
        "symbol": symbol,
        "ic": round(float(ic), 4),
        "p_value": round(float(p_value), 4),
        "window_days": days,
        "sample_count": len(predictions),
        "is_significant": p_value < 0.05,
        "ic_quality": "Strong" if abs(ic) > 0.05 else "Weak" if abs(ic) > 0.02 else "Noise",
    }


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=5001)
