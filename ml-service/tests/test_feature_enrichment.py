"""Tests for ml_models.feature_enrichment — merges funding, OI, basis into bars."""
import pandas as pd
import pytest

from ml_models.feature_enrichment import enrich_with_derivatives


@pytest.fixture
def bars():
    return pd.DataFrame({
        "time":   pd.date_range("2024-01-01", periods=10, freq="1h", tz="UTC"),
        "open":   [100.0] * 10,
        "high":   [101.0] * 10,
        "low":    [99.0] * 10,
        "close":  [100.5] * 10,
        "volume": [1000.0] * 10,
    })


@pytest.fixture
def funding():
    # Binance funding runs every 8h
    return pd.DataFrame({
        "time": pd.to_datetime(["2024-01-01 00:00", "2024-01-01 08:00"], utc=True),
        "funding_rate": [0.0001, 0.0002],
    })


@pytest.fixture
def oi():
    return pd.DataFrame({
        "time": pd.date_range("2024-01-01", periods=10, freq="1h", tz="UTC"),
        "open_interest": [1000.0 + i * 10 for i in range(10)],
    })


def test_enrich_forward_fills_funding(bars, funding, oi):
    out = enrich_with_derivatives(bars, funding=funding, oi=oi)
    # First bar 00:00 picks up funding_rate=0.0001; all bars until 08:00 keep 0.0001
    assert out["funding_rate"].iloc[0] == pytest.approx(0.0001)
    assert out["funding_rate"].iloc[7] == pytest.approx(0.0001)
    assert out["funding_rate"].iloc[8] == pytest.approx(0.0002)


def test_enrich_preserves_bar_count(bars, funding, oi):
    out = enrich_with_derivatives(bars, funding=funding, oi=oi)
    assert len(out) == len(bars)


def test_enrich_computes_oi_delta(bars, funding, oi):
    out = enrich_with_derivatives(bars, funding=funding, oi=oi)
    # oi goes from 1000 → 1090 linearly; oi_delta_1 is the per-bar change
    assert out["oi_delta_1"].iloc[1] == pytest.approx(10.0)


def test_enrich_missing_optional_frames_is_graceful(bars):
    """funding/oi are optional; absence yields 0-filled columns, not NaN."""
    out = enrich_with_derivatives(bars, funding=None, oi=None)
    assert (out["funding_rate"] == 0.0).all()
    assert (out["oi_delta_1"] == 0.0).all()


def test_enrich_accepts_tz_naive_inputs(bars, funding, oi):
    """merge_asof crashes on mixed tz-aware/tz-naive keys; we coerce to UTC."""
    naive_bars = bars.copy()
    naive_bars["time"] = naive_bars["time"].dt.tz_localize(None)
    naive_funding = funding.copy()
    naive_funding["time"] = naive_funding["time"].dt.tz_localize(None)

    out = enrich_with_derivatives(naive_bars, funding=naive_funding, oi=oi)
    # Still produces the same forward-filled first value
    assert out["funding_rate"].iloc[0] == pytest.approx(0.0001)


def test_enrich_no_look_ahead(bars, funding, oi):
    """Enrichment at bar i must only use data with time <= bars.time[i]."""
    out_full = enrich_with_derivatives(bars, funding=funding, oi=oi)
    out_truncated = enrich_with_derivatives(bars.iloc[:5].copy(), funding=funding, oi=oi)
    # Row 4 must be identical in both frames
    pd.testing.assert_series_equal(
        out_full.iloc[4][["funding_rate", "oi_delta_1"]],
        out_truncated.iloc[4][["funding_rate", "oi_delta_1"]],
        check_names=False,
    )
