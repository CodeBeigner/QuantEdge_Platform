"""Tests for primary_signals — minimal rules-based replay for meta-labeler bootstrap."""
import numpy as np
import pandas as pd
import pytest

from ml_models.primary_signals import replay_momentum_primary


@pytest.fixture
def rising_series():
    closes = np.linspace(100.0, 200.0, 300)
    return pd.DataFrame({
        "time": pd.date_range("2024-01-01", periods=300, freq="15min", tz="UTC"),
        "open":   closes,
        "high":   closes * 1.001,
        "low":    closes * 0.999,
        "close":  closes,
        "volume": np.ones(300) * 1000.0,
    })


@pytest.fixture
def falling_series():
    closes = np.linspace(200.0, 100.0, 300)
    return pd.DataFrame({
        "time": pd.date_range("2024-01-01", periods=300, freq="15min", tz="UTC"),
        "open":   closes,
        "high":   closes * 1.001,
        "low":    closes * 0.999,
        "close":  closes,
        "volume": np.ones(300) * 1000.0,
    })


def test_replay_emits_long_signals_in_uptrend(rising_series):
    signals = replay_momentum_primary(rising_series, fast=10, slow=50)
    assert (signals["direction"] == 1).all()
    assert len(signals) > 0


def test_replay_emits_short_signals_in_downtrend(falling_series):
    signals = replay_momentum_primary(falling_series, fast=10, slow=50)
    assert (signals["direction"] == -1).all()
    assert len(signals) > 0


def test_replay_emits_one_signal_per_cross(rising_series):
    """Cross events, not every bar — check signal count is reasonable."""
    signals = replay_momentum_primary(rising_series, fast=10, slow=50)
    # In a pure monotone rise, fast SMA crosses slow SMA exactly once
    assert len(signals) == 1


def test_replay_output_columns(rising_series):
    signals = replay_momentum_primary(rising_series, fast=10, slow=50)
    assert list(signals.columns) == ["time", "direction"]


def test_replay_rejects_when_slow_ge_len(rising_series):
    with pytest.raises(ValueError, match="not enough bars"):
        replay_momentum_primary(rising_series.iloc[:20], fast=10, slow=50)
