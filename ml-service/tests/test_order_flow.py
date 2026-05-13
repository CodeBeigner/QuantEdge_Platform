"""Tests for ml_models.order_flow — LightGBM directional predictor on flow features."""
import numpy as np
import pandas as pd
import pytest

from ml_models.order_flow import OrderFlowModel, compute_flow_features


@pytest.fixture
def synthetic_bars():
    rng = np.random.default_rng(7)
    n = 500
    closes = 100 + np.cumsum(rng.normal(0, 0.5, n))
    volumes = rng.uniform(100, 1000, n)
    return pd.DataFrame({
        "time":   pd.date_range("2024-01-01", periods=n, freq="15min", tz="UTC"),
        "open":   closes,
        "high":   closes + 0.2,
        "low":    closes - 0.2,
        "close":  closes,
        "volume": volumes,
        "funding_rate": rng.normal(0, 0.0001, n),
        "funding_rate_delta": rng.normal(0, 0.0001, n),
        "open_interest": np.cumsum(rng.normal(0, 5, n)) + 1000,
        "oi_delta_1": rng.normal(0, 5, n),
        "oi_delta_4": rng.normal(0, 10, n),
    })


def test_compute_flow_features_returns_expected_cols(synthetic_bars):
    out = compute_flow_features(synthetic_bars)
    for col in ["cvd", "aggressive_buy_ratio_20", "funding_rate", "oi_delta_1"]:
        assert col in out.columns


def test_compute_flow_features_no_look_ahead(synthetic_bars):
    full = compute_flow_features(synthetic_bars)
    trunc = compute_flow_features(synthetic_bars.iloc[:100].copy())
    for col in ["cvd", "aggressive_buy_ratio_20"]:
        v_full = full[col].iloc[99]
        v_tr   = trunc[col].iloc[99]
        if pd.isna(v_full) and pd.isna(v_tr):
            continue
        assert v_full == pytest.approx(v_tr, rel=1e-9, abs=1e-9), f"{col} leaks"


def test_train_returns_metrics(synthetic_bars):
    model = OrderFlowModel()
    result = model.train(synthetic_bars, forward_bars=4)
    assert "train_accuracy" in result
    assert "n_train" in result
    assert result["n_train"] > 0


def test_predict_output_shape(synthetic_bars):
    model = OrderFlowModel()
    model.train(synthetic_bars, forward_bars=4)
    row = compute_flow_features(synthetic_bars).iloc[[-1]]
    out = model.predict(row)
    assert "flow_score" in out
    assert "direction" in out
    assert out["direction"] in (-1, 0, 1)


def test_predict_before_training_raises(synthetic_bars):
    model = OrderFlowModel()
    with pytest.raises(RuntimeError, match="not trained"):
        model.predict(synthetic_bars.iloc[[0]])
