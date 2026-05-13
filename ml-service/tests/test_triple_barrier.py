"""Tests for triple_barrier labeler — path-aware labels without look-ahead."""
import numpy as np
import pandas as pd
import pytest

from labelers.triple_barrier import apply_triple_barrier


def _ramp_up_prices(start: float = 100.0, n: int = 50, step: float = 0.01):
    """Monotonically rising prices — every long trade should hit TP."""
    closes = np.array([start * (1 + step) ** i for i in range(n)])
    times = pd.date_range("2024-01-01", periods=n, freq="15min", tz="UTC")
    return pd.DataFrame({"time": times, "close": closes})


def _ramp_down_prices(start: float = 100.0, n: int = 50, step: float = 0.01):
    """Monotonically falling prices."""
    closes = np.array([start * (1 - step) ** i for i in range(n)])
    times = pd.date_range("2024-01-01", periods=n, freq="15min", tz="UTC")
    return pd.DataFrame({"time": times, "close": closes})


def test_long_signal_hits_tp_on_rising_prices():
    bars = _ramp_up_prices()
    signals = pd.DataFrame({"time": [bars["time"].iloc[0]], "direction": [1]})

    labels = apply_triple_barrier(bars, signals, tp_pct=0.02, sl_pct=0.01, max_bars=40)

    assert len(labels) == 1
    assert labels["label"].iloc[0] == 1  # TP hit
    assert labels["outcome"].iloc[0] == "TP"
    # Outcome index must be within max_bars
    assert labels["outcome_bar"].iloc[0] < 40


def test_long_signal_hits_sl_on_falling_prices():
    bars = _ramp_down_prices()
    signals = pd.DataFrame({"time": [bars["time"].iloc[0]], "direction": [1]})

    labels = apply_triple_barrier(bars, signals, tp_pct=0.02, sl_pct=0.01, max_bars=40)

    assert labels["label"].iloc[0] == 0
    assert labels["outcome"].iloc[0] == "SL"


def test_short_signal_hits_tp_on_falling_prices():
    bars = _ramp_down_prices()
    signals = pd.DataFrame({"time": [bars["time"].iloc[0]], "direction": [-1]})

    labels = apply_triple_barrier(bars, signals, tp_pct=0.02, sl_pct=0.01, max_bars=40)

    assert labels["label"].iloc[0] == 1
    assert labels["outcome"].iloc[0] == "TP"


def test_max_bars_timeout_returns_minus_one():
    """Flat prices — neither TP nor SL hit within max_bars."""
    bars = pd.DataFrame({
        "time": pd.date_range("2024-01-01", periods=30, freq="15min", tz="UTC"),
        "close": [100.0] * 30,
    })
    signals = pd.DataFrame({"time": [bars["time"].iloc[0]], "direction": [1]})

    labels = apply_triple_barrier(bars, signals, tp_pct=0.02, sl_pct=0.01, max_bars=20)

    assert labels["label"].iloc[0] == -1
    assert labels["outcome"].iloc[0] == "TIMEOUT"


def test_rejects_signal_without_enough_forward_bars():
    """A signal within max_bars of the end of the data must be dropped, not crashed on."""
    bars = _ramp_up_prices(n=30)
    # Signal at bar 25 with max_bars=20 can't observe a full horizon.
    signals = pd.DataFrame({"time": [bars["time"].iloc[25]], "direction": [1]})

    labels = apply_triple_barrier(bars, signals, tp_pct=0.02, sl_pct=0.01, max_bars=20)

    # Either dropped or labeled with the observed partial horizon — but never crashed.
    # Our contract: drop rows that can't observe a full max_bars horizon.
    assert len(labels) == 0


def test_rejects_non_unit_directions():
    bars = _ramp_up_prices()
    signals = pd.DataFrame({"time": [bars["time"].iloc[0]], "direction": [2]})

    with pytest.raises(ValueError, match="direction must be"):
        apply_triple_barrier(bars, signals, tp_pct=0.02, sl_pct=0.01, max_bars=20)


def test_requires_positive_tp_and_sl():
    bars = _ramp_up_prices()
    signals = pd.DataFrame({"time": [bars["time"].iloc[0]], "direction": [1]})

    with pytest.raises(ValueError, match="must be positive"):
        apply_triple_barrier(bars, signals, tp_pct=-0.01, sl_pct=0.01, max_bars=20)


def test_output_columns():
    bars = _ramp_up_prices()
    signals = pd.DataFrame({"time": [bars["time"].iloc[0]], "direction": [1]})

    labels = apply_triple_barrier(bars, signals, tp_pct=0.02, sl_pct=0.01, max_bars=20)

    assert list(labels.columns) == [
        "signal_time", "direction", "entry_price", "label", "outcome",
        "outcome_time", "outcome_bar", "return_pct",
    ]
