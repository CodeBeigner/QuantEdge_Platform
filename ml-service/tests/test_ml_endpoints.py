"""Tests for the new /train-meta, /predict-meta, /train-flow, /predict-flow endpoints.

Uses FastAPI's TestClient. Backend HTTP calls (to the Java /market-data endpoint) are
patched to return synthetic OHLCV so tests don't require a running backend.
"""
from unittest.mock import patch

import numpy as np
import pandas as pd
import pytest
from fastapi.testclient import TestClient


@pytest.fixture
def synthetic_bars_df():
    n = 3000
    rng = np.random.default_rng(0)
    closes = 100 + np.cumsum(rng.normal(0, 1.5, n))
    return pd.DataFrame({
        "time":   pd.date_range("2024-01-01", periods=n, freq="15min", tz="UTC"),
        "open":   closes,
        "high":   closes + 0.3,
        "low":    closes - 0.3,
        "close":  closes,
        "volume": np.ones(n) * 1000,
    })


@pytest.fixture
def client(synthetic_bars_df, tmp_path, monkeypatch):
    monkeypatch.setenv("ML_MODEL_DIR", str(tmp_path))

    import importlib
    import main as app_main
    importlib.reload(app_main)

    async def fake_fetch(symbol: str, days: int = 500):
        return synthetic_bars_df.copy()

    app_main.fetch_market_data = fake_fetch  # monkeypatch inside module
    return TestClient(app_main.app)


def test_train_meta_returns_200(client):
    resp = client.post("/train-meta/BTCUSDT")
    assert resp.status_code == 200
    body = resp.json()
    assert "n_train" in body
    assert body["n_train"] > 0


def test_predict_meta_after_train_returns_probability(client):
    client.post("/train-meta/BTCUSDT")
    resp = client.post("/predict-meta/BTCUSDT", json={
        "primary_signal": "LONG",
        "entry_price": 100.0,
        "tp_pct": 0.02,
        "sl_pct": 0.01,
    })
    assert resp.status_code == 200
    body = resp.json()
    assert 0.0 <= body["meta_prob"] <= 1.0
    assert body["direction"] == 1


def test_predict_meta_without_training_returns_400(client):
    resp = client.post("/predict-meta/NEWPAIR", json={
        "primary_signal": "LONG",
        "entry_price": 100.0,
    })
    assert resp.status_code == 400


def test_train_flow_returns_200(client):
    resp = client.post("/train-flow/BTCUSDT")
    assert resp.status_code == 200
    body = resp.json()
    assert "n_train" in body


def test_predict_flow_after_train_returns_score(client):
    client.post("/train-flow/BTCUSDT")
    resp = client.post("/predict-flow/BTCUSDT", json={"lookback_bars": 100})
    assert resp.status_code == 200
    body = resp.json()
    assert "flow_score" in body
    assert body["direction"] in (-1, 0, 1)
