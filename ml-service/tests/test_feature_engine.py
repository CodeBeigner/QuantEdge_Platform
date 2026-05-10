"""Tests for feature_engine.compute_features — no look-ahead bias allowed."""
import numpy as np
import pandas as pd
import pytest

from feature_engine import compute_features, FEATURE_COLS


@pytest.fixture
def sample_ohlcv():
    """200 rows of synthetic OHLCV — enough for 50-period SMAs to stabilize."""
    rng = np.random.default_rng(42)
    close = 100 + np.cumsum(rng.normal(0, 1, 200))
    high = close + rng.uniform(0.1, 1.0, 200)
    low = close - rng.uniform(0.1, 1.0, 200)
    openp = close + rng.uniform(-0.5, 0.5, 200)
    volume = rng.uniform(100, 1000, 200)
    return pd.DataFrame({
        "open": openp, "high": high, "low": low, "close": close, "volume": volume,
    })


def test_no_target_column(sample_ohlcv):
    """compute_features must NOT emit a 'target' column — prevents shipping look-ahead labels to downstream."""
    out = compute_features(sample_ohlcv)
    assert "target" not in out.columns, (
        "feature_engine must not emit a target column; labeling is the caller's responsibility"
    )


def test_no_future_leakage_in_feature_row(sample_ohlcv):
    """A feature row at index i must be computable from bars [0..i], never from bar i+1 onwards."""
    df = sample_ohlcv.copy()
    full = compute_features(df)
    # Compute features on a truncated frame ending at index 150.
    # The feature row at index 150 in the full frame must equal the feature row at index 150 in the truncated frame.
    truncated = compute_features(df.iloc[:151].copy())
    for col in FEATURE_COLS:
        full_val = full[col].iloc[150]
        trunc_val = truncated[col].iloc[150]
        if pd.isna(full_val) and pd.isna(trunc_val):
            continue
        assert full_val == pytest.approx(trunc_val, rel=1e-9, abs=1e-9), (
            f"Feature {col} at row 150 differs when future rows are hidden — look-ahead bug"
        )


def test_feature_cols_all_present(sample_ohlcv):
    """All feature columns declared in FEATURE_COLS must appear in output."""
    out = compute_features(sample_ohlcv)
    for col in FEATURE_COLS:
        assert col in out.columns, f"Feature column {col} missing from output"
