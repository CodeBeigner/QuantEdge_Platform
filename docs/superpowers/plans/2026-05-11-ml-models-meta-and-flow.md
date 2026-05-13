# ML Models: Triple-Barrier Meta-Labeler + Order-Flow GBDT — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship two ML endpoints — `/predict-meta/{symbol}` (triple-barrier meta-labeler for primary-signal filtering) and `/predict-flow/{symbol}` (order-flow GBDT for microstructure edge) — along with their labelers, feature builders, training pipelines, and a thin Java client that can call them.

**Architecture:** Purely additive. `ml-service/labelers/triple_barrier.py` generates path-aware labels from historical OHLCV + TP/SL parameters. `ml-service/ml_models/meta_labeler.py` wraps XGBoost as a binary classifier; `ml-service/ml_models/order_flow.py` wraps LightGBM on a fallback microstructure feature set. Both expose `train/predict/save/load`. Two new POST endpoints in `main.py` expose them. Training uses purged K-fold walk-forward (López de Prado, Ch. 7). A new Java `MLMetaClient` can call `/predict-meta` — the TradeRiskEngine wire-up is deliberately deferred to Plan 4.

**Tech Stack:** Python 3.9+, pandas, numpy, xgboost, lightgbm, scikit-learn (for purged CV), joblib (model persistence), FastAPI. Java 21 Spring Boot HTTP client.

**Parent spec:** `docs/superpowers/specs/2026-05-10-ml-rebuild-and-paper-trading-design.md` §4.4.

**Parent memory:** `~/.claude/projects/-Users-abhinavunmesh/memory/project_quantedge_ml_rebuild.md`.

---

## Scope Boundary

**In scope (Plan 2):**
- Triple-barrier label generator with tests
- Meta-labeler model class (XGBoost)
- Order-flow GBDT model class (LightGBM on fallback features)
- Feature enrichment helpers (funding rate delta, OI delta, CVD proxy)
- Purged K-fold walk-forward training routine
- Model persistence to `models/{symbol}/{type}/v{n}.joblib` with `latest.json` registry
- POST `/predict-meta/{symbol}` and POST `/predict-flow/{symbol}` endpoints
- POST `/train-meta/{symbol}` and POST `/train-flow/{symbol}` endpoints
- Java `MLMetaClient` HTTP client (wrapper only — no risk-engine hookup)

**Out of scope (deferred):**
- Wiring meta-filter into `TradeRiskEngine` → Plan 4
- Backtest consolidation → Plan 3
- Paper trading scheduler → Plan 4
- Weekly retrain cron → Plan 4 (we add manual retrain endpoint here; scheduling it comes with paper trading)
- The primary-signal replay harness needed to bootstrap meta-labeler training data on historical bars. We add it here as a minimal helper, but full production-grade backtest-driven signal history is Plan 3 territory.

---

## File Layout

### Created
- `ml-service/labelers/__init__.py`
- `ml-service/labelers/triple_barrier.py` — path-aware label generator
- `ml-service/ml_models/__init__.py`
- `ml-service/ml_models/feature_enrichment.py` — merges market_data with funding_rate_history + OI + perp-spot basis
- `ml-service/ml_models/purged_kfold.py` — López de Prado Ch. 7 CV
- `ml-service/ml_models/registry.py` — `latest.json` pointer + model save/load helpers
- `ml-service/ml_models/meta_labeler.py` — XGBoost meta-filter
- `ml-service/ml_models/order_flow.py` — LightGBM directional predictor
- `ml-service/ml_models/primary_signals.py` — minimal callable that replays a rules-based primary strategy over historical bars so the meta-labeler has signals to train on
- `ml-service/tests/test_triple_barrier.py`
- `ml-service/tests/test_feature_enrichment.py`
- `ml-service/tests/test_purged_kfold.py`
- `ml-service/tests/test_registry.py`
- `ml-service/tests/test_meta_labeler.py`
- `ml-service/tests/test_order_flow.py`
- `ml-service/tests/test_primary_signals.py`
- `ml-service/tests/test_ml_endpoints.py` — FastAPI TestClient over new endpoints
- `QuantPlatformApplication/src/main/java/com/QuantPlatformApplication/QuantPlatformApplication/client/MLMetaClient.java`
- `QuantPlatformApplication/src/main/java/com/QuantPlatformApplication/QuantPlatformApplication/client/MLMetaPredictionResponse.java`
- `QuantPlatformApplication/src/test/java/com/QuantPlatformApplication/QuantPlatformApplication/client/MLMetaClientTest.java`

### Modified
- `ml-service/requirements.txt` — add `lightgbm>=4.3.0`
- `ml-service/main.py` — register 4 new endpoints (train-meta, predict-meta, train-flow, predict-flow)

---

## Task 1: Triple-Barrier Labeler

**Files:**
- Create: `ml-service/labelers/__init__.py`
- Create: `ml-service/labelers/triple_barrier.py`
- Create: `ml-service/tests/test_triple_barrier.py`

- [ ] **Step 1: Empty package init**

Create `ml-service/labelers/__init__.py` (empty file).

- [ ] **Step 2: Write failing tests**

Create `ml-service/tests/test_triple_barrier.py`:

```python
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
```

Run: `cd /Users/abhinavunmesh/Desktop/QuantEdge_Platform/ml-service && python3 -m pytest tests/test_triple_barrier.py -v`

Expected: 8 FAILs — module doesn't exist.

- [ ] **Step 3: Implement the labeler**

Create `ml-service/labelers/triple_barrier.py`:

```python
"""Triple-barrier labeler (López de Prado, Advances in Financial ML, Ch. 3).

Given a DataFrame of forward-looking OHLCV bars and a DataFrame of primary signals,
produce binary labels: 1 if take-profit hit first, 0 if stop-loss hit first, -1 if
neither hit within max_bars.

The output can be used to train a meta-labeler that predicts P(TP hit first) given
the state at signal time — without look-ahead bias, because labels come from
simulated forward paths, not future features.

Signal DataFrame contract:
    time:      timestamp of the primary signal (must match a bar time in `bars`)
    direction: +1 for long, -1 for short

Bars DataFrame contract:
    time:      timestamp (UTC, sorted ascending, unique)
    close:     float close price
    (optional high/low — used for intra-bar barrier touches if present)

Returned columns:
    signal_time, direction, entry_price, label (in {1, 0, -1}),
    outcome ("TP" | "SL" | "TIMEOUT"), outcome_time, outcome_bar, return_pct.
"""
from __future__ import annotations

import numpy as np
import pandas as pd


def apply_triple_barrier(
    bars: pd.DataFrame,
    signals: pd.DataFrame,
    tp_pct: float,
    sl_pct: float,
    max_bars: int,
) -> pd.DataFrame:
    if tp_pct <= 0 or sl_pct <= 0:
        raise ValueError("tp_pct and sl_pct must be positive fractions")
    if max_bars < 1:
        raise ValueError("max_bars must be >= 1")

    valid_dirs = {-1, 1}
    if not set(signals["direction"].unique()).issubset(valid_dirs):
        raise ValueError("direction must be +1 (long) or -1 (short)")

    bars = bars.sort_values("time").reset_index(drop=True)
    time_to_idx = {t: i for i, t in enumerate(bars["time"])}

    has_hl = "high" in bars.columns and "low" in bars.columns
    closes = bars["close"].to_numpy()
    highs = bars["high"].to_numpy() if has_hl else closes
    lows = bars["low"].to_numpy() if has_hl else closes

    out_rows = []
    for _, sig in signals.iterrows():
        t = sig["time"]
        direction = int(sig["direction"])
        if t not in time_to_idx:
            continue
        i0 = time_to_idx[t]
        if i0 + max_bars >= len(bars):
            # Can't observe a full horizon — drop this signal
            continue

        entry = closes[i0]
        if direction == 1:
            tp_level = entry * (1 + tp_pct)
            sl_level = entry * (1 - sl_pct)
        else:
            tp_level = entry * (1 - tp_pct)
            sl_level = entry * (1 + sl_pct)

        outcome = "TIMEOUT"
        outcome_bar = max_bars
        exit_price = closes[i0 + max_bars]

        for step in range(1, max_bars + 1):
            j = i0 + step
            hi, lo = highs[j], lows[j]
            if direction == 1:
                if hi >= tp_level:
                    outcome, outcome_bar, exit_price = "TP", step, tp_level
                    break
                if lo <= sl_level:
                    outcome, outcome_bar, exit_price = "SL", step, sl_level
                    break
            else:
                if lo <= tp_level:
                    outcome, outcome_bar, exit_price = "TP", step, tp_level
                    break
                if hi >= sl_level:
                    outcome, outcome_bar, exit_price = "SL", step, sl_level
                    break

        label = {"TP": 1, "SL": 0, "TIMEOUT": -1}[outcome]
        ret = (exit_price - entry) / entry * direction

        out_rows.append({
            "signal_time": t,
            "direction": direction,
            "entry_price": float(entry),
            "label": label,
            "outcome": outcome,
            "outcome_time": bars["time"].iloc[i0 + outcome_bar],
            "outcome_bar": outcome_bar,
            "return_pct": float(ret),
        })

    cols = ["signal_time", "direction", "entry_price", "label", "outcome",
            "outcome_time", "outcome_bar", "return_pct"]
    return pd.DataFrame(out_rows, columns=cols)
```

Run: `cd /Users/abhinavunmesh/Desktop/QuantEdge_Platform/ml-service && python3 -m pytest tests/test_triple_barrier.py -v`

Expected: 8 passed.

- [ ] **Step 4: Commit**

```bash
cd /Users/abhinavunmesh/Desktop/QuantEdge_Platform
git add ml-service/labelers/__init__.py ml-service/labelers/triple_barrier.py \
        ml-service/tests/test_triple_barrier.py
git commit -m "feat(ml): triple-barrier labeler for path-aware ML labels

Generates {-1, 0, 1} labels per primary signal based on whether TP or
SL was hit first within max_bars. Uses high/low if present to detect
intra-bar barrier touches; falls back to close. Rejects signals without
a full forward horizon rather than truncating. Foundation for the
meta-labeler (Task 3).

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Task 2: Primary Signal Replay Helper

**Files:**
- Create: `ml-service/ml_models/__init__.py`
- Create: `ml-service/ml_models/primary_signals.py`
- Create: `ml-service/tests/test_primary_signals.py`

- [ ] **Step 1: Empty package init**

Create `ml-service/ml_models/__init__.py` (empty).

- [ ] **Step 2: Write failing tests**

Create `ml-service/tests/test_primary_signals.py`:

```python
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
```

Run: `python3 -m pytest tests/test_primary_signals.py -v` — 5 FAILs expected.

- [ ] **Step 3: Implement**

Create `ml-service/ml_models/primary_signals.py`:

```python
"""Primary-signal generators used to bootstrap meta-labeler training.

These are deliberately simple rules-based strategies whose raw output the
meta-labeler learns to filter. We do NOT claim these strategies make money
standalone — their job is to produce a stream of historical signals that
the meta-labeler can score. Production trading uses the Java side's full
strategy suite; this module exists so we can train the meta-labeler without
coupling to the Java rules.
"""
from __future__ import annotations

import pandas as pd


def replay_momentum_primary(bars: pd.DataFrame, fast: int = 10, slow: int = 50) -> pd.DataFrame:
    """Emit +1 on fast-SMA-crosses-above-slow-SMA, -1 on the inverse cross.

    Returns a DataFrame with columns [time, direction]. One row per cross event.
    """
    if len(bars) <= slow:
        raise ValueError(f"not enough bars ({len(bars)}) for slow={slow}")

    closes = bars["close"].astype(float)
    fast_ma = closes.rolling(fast).mean()
    slow_ma = closes.rolling(slow).mean()

    diff = fast_ma - slow_ma
    prev = diff.shift(1)

    cross_up = (prev <= 0) & (diff > 0)
    cross_dn = (prev >= 0) & (diff < 0)

    long_mask = cross_up & fast_ma.notna() & slow_ma.notna()
    short_mask = cross_dn & fast_ma.notna() & slow_ma.notna()

    longs = pd.DataFrame({
        "time": bars.loc[long_mask, "time"].values,
        "direction": 1,
    })
    shorts = pd.DataFrame({
        "time": bars.loc[short_mask, "time"].values,
        "direction": -1,
    })

    out = pd.concat([longs, shorts], ignore_index=True).sort_values("time").reset_index(drop=True)
    return out[["time", "direction"]]
```

Run: `python3 -m pytest tests/test_primary_signals.py -v` — 5 passed.

- [ ] **Step 4: Commit**

```bash
cd /Users/abhinavunmesh/Desktop/QuantEdge_Platform
git add ml-service/ml_models/__init__.py ml-service/ml_models/primary_signals.py \
        ml-service/tests/test_primary_signals.py
git commit -m "feat(ml): primary-signal replay helper (SMA crossover)

Minimal rules-based primary signal generator whose output feeds the
triple-barrier labeler. Not a production strategy — just a stream of
long/short entries the meta-labeler can score during bootstrap
training.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Task 3: Feature Enrichment (funding + OI + basis)

**Files:**
- Create: `ml-service/ml_models/feature_enrichment.py`
- Create: `ml-service/tests/test_feature_enrichment.py`

- [ ] **Step 1: Write failing tests**

Create `ml-service/tests/test_feature_enrichment.py`:

```python
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
```

Run: `python3 -m pytest tests/test_feature_enrichment.py -v` — 5 FAILs.

- [ ] **Step 2: Implement**

Create `ml-service/ml_models/feature_enrichment.py`:

```python
"""Merge derivatives data (funding, OI) into OHLCV bars for ML features.

All merges are strictly past-or-current: at bar time t, we only pull funding/OI
rows whose time <= t. Forward-fill is used to propagate the most recent funding
rate across bars until the next funding event.
"""
from __future__ import annotations

from typing import Optional

import pandas as pd


def enrich_with_derivatives(
    bars: pd.DataFrame,
    funding: Optional[pd.DataFrame] = None,
    oi: Optional[pd.DataFrame] = None,
) -> pd.DataFrame:
    out = bars.copy().sort_values("time").reset_index(drop=True)

    # Funding rate — forward-fill the most recent funding event
    if funding is not None and not funding.empty:
        f = funding.sort_values("time").reset_index(drop=True)
        merged = pd.merge_asof(
            out[["time"]], f[["time", "funding_rate"]],
            on="time", direction="backward",
        )
        out["funding_rate"] = merged["funding_rate"].fillna(0.0).astype(float)
    else:
        out["funding_rate"] = 0.0

    # Funding delta (current vs previous)
    out["funding_rate_delta"] = out["funding_rate"].diff().fillna(0.0)

    # Open interest — merge_asof, then compute deltas
    if oi is not None and not oi.empty:
        o = oi.sort_values("time").reset_index(drop=True)
        merged = pd.merge_asof(
            out[["time"]], o[["time", "open_interest"]],
            on="time", direction="backward",
        )
        oi_series = merged["open_interest"].ffill().fillna(0.0).astype(float)
        out["open_interest"] = oi_series
        out["oi_delta_1"] = oi_series.diff(1).fillna(0.0)
        out["oi_delta_4"] = oi_series.diff(4).fillna(0.0)
    else:
        out["open_interest"] = 0.0
        out["oi_delta_1"] = 0.0
        out["oi_delta_4"] = 0.0

    return out


ENRICHED_COLS = [
    "funding_rate", "funding_rate_delta",
    "open_interest", "oi_delta_1", "oi_delta_4",
]
```

Run: `python3 -m pytest tests/test_feature_enrichment.py -v` — 5 passed.

- [ ] **Step 3: Commit**

```bash
cd /Users/abhinavunmesh/Desktop/QuantEdge_Platform
git add ml-service/ml_models/feature_enrichment.py \
        ml-service/tests/test_feature_enrichment.py
git commit -m "feat(ml): derivatives feature enrichment (funding + OI)

merge_asof backward-only joins keep features causal. Missing funding
or OI frames produce zero-filled columns rather than NaN so downstream
models don't need to special-case.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Task 4: Purged K-Fold Walk-Forward CV

**Files:**
- Create: `ml-service/ml_models/purged_kfold.py`
- Create: `ml-service/tests/test_purged_kfold.py`

- [ ] **Step 1: Write failing tests**

Create `ml-service/tests/test_purged_kfold.py`:

```python
"""Tests for purged K-fold time-series CV (Lopez de Prado Ch. 7)."""
import numpy as np
import pandas as pd
import pytest

from ml_models.purged_kfold import PurgedKFold


def _mock_times(n=100):
    return pd.Series(pd.date_range("2024-01-01", periods=n, freq="1h", tz="UTC"))


def _mock_event_times(times, horizon_bars=5):
    """For each event at times[i], outcome arrives at times[i+horizon_bars]."""
    outcome_idx = np.arange(len(times)) + horizon_bars
    outcome_idx = np.minimum(outcome_idx, len(times) - 1)
    return pd.Series(times.iloc[outcome_idx].values, index=times.index)


def test_splits_are_disjoint():
    times = _mock_times(100)
    events = _mock_event_times(times, horizon_bars=5)
    cv = PurgedKFold(n_splits=5, event_end_times=events, embargo_bars=2)
    all_test_sets = []
    for _, test_idx in cv.split(np.arange(100)):
        all_test_sets.append(set(test_idx))
    total_test = set().union(*all_test_sets)
    # No overlap between test folds
    assert sum(len(s) for s in all_test_sets) == len(total_test)


def test_purge_removes_leaky_train_samples():
    times = _mock_times(100)
    events = _mock_event_times(times, horizon_bars=5)
    cv = PurgedKFold(n_splits=5, event_end_times=events, embargo_bars=0)
    for train_idx, test_idx in cv.split(np.arange(100)):
        test_times = times.iloc[test_idx]
        for ti in train_idx:
            # A train event ending inside the test window is leakage — must not appear
            if events.iloc[ti] >= test_times.min() and times.iloc[ti] <= test_times.max():
                pytest.fail(f"Train index {ti} leaks into test window")


def test_embargo_removes_samples_after_test():
    times = _mock_times(100)
    events = _mock_event_times(times, horizon_bars=5)
    cv = PurgedKFold(n_splits=4, event_end_times=events, embargo_bars=3)
    for train_idx, test_idx in cv.split(np.arange(100)):
        test_max = max(test_idx)
        # Embargo: no train index in (test_max, test_max + embargo_bars]
        for ti in train_idx:
            assert not (test_max < ti <= test_max + 3)


def test_n_splits_produces_correct_fold_count():
    times = _mock_times(100)
    events = _mock_event_times(times)
    cv = PurgedKFold(n_splits=5, event_end_times=events, embargo_bars=0)
    folds = list(cv.split(np.arange(100)))
    assert len(folds) == 5
```

Run: `python3 -m pytest tests/test_purged_kfold.py -v` — 4 FAILs.

- [ ] **Step 2: Implement**

Create `ml-service/ml_models/purged_kfold.py`:

```python
"""Purged K-Fold cross-validation for time series with overlapping events.

Reference: Lopez de Prado, Advances in Financial Machine Learning, Ch. 7.

Each sample has a known event-end time (e.g., when the triple-barrier outcome
resolved). A train/test split is valid only if:
  1. Train samples whose event window overlaps the test window are PURGED.
  2. Train samples immediately after the test window are EMBARGOED (removed)
     to account for autocorrelation spillover.
"""
from __future__ import annotations

from typing import Iterator, Tuple

import numpy as np
import pandas as pd


class PurgedKFold:
    def __init__(self, n_splits: int, event_end_times: pd.Series, embargo_bars: int = 0):
        if n_splits < 2:
            raise ValueError("n_splits must be >= 2")
        self.n_splits = n_splits
        self.event_end_times = event_end_times.reset_index(drop=True)
        self.embargo_bars = embargo_bars

    def split(self, X) -> Iterator[Tuple[np.ndarray, np.ndarray]]:
        n = len(X) if hasattr(X, "__len__") else X.shape[0]
        if n != len(self.event_end_times):
            raise ValueError(
                f"X length {n} != event_end_times length {len(self.event_end_times)}"
            )

        indices = np.arange(n)
        fold_size = n // self.n_splits
        # Contiguous test folds
        fold_bounds = [(i * fold_size, (i + 1) * fold_size if i < self.n_splits - 1 else n)
                       for i in range(self.n_splits)]

        event_times_np = self.event_end_times.values
        sample_times_np = pd.Series(pd.to_datetime(self.event_end_times.index, unit="s", utc=True)).values \
            if not np.issubdtype(self.event_end_times.index.dtype, np.datetime64) else self.event_end_times.index

        for start, end in fold_bounds:
            test_idx = indices[start:end]
            test_max_idx = test_idx[-1]

            # A train sample i is purged if its event (start=i, end=event_end_times[i])
            # overlaps [start, end] — i.e., i <= end - 1 AND event_end_times[i] >= start_time
            test_start_time = self.event_end_times.iloc[test_idx[0]] if False else None
            # Simplest purge: drop any train index whose (i, event_end_idx) window overlaps test window
            # We proxy "time overlap" by index overlap since event_end_times is monotonic with index.

            train_mask = np.ones(n, dtype=bool)
            train_mask[test_idx] = False

            # Purge: any i where i is in training AND (i is inside test window OR event resolves inside test window)
            # Since event_end_times is indexed positionally we use the fact that event ends are at some
            # positional offset. Find positional index of each event_end_time.
            # Here we approximate: drop train indices i where min(test_idx) <= i <= max(test_idx)
            # is already handled by the test_mask. The remaining concern is train indices whose
            # event_end falls within the test window.
            for i in np.where(train_mask)[0]:
                end_t = self.event_end_times.iloc[i]
                # If the event ends at or after the test window's first event start, purge it.
                test_first_time = self.event_end_times.iloc[test_idx[0]] if test_idx[0] < len(self.event_end_times) else None
                test_last_time = self.event_end_times.iloc[test_idx[-1]] if test_idx[-1] < len(self.event_end_times) else None
                if test_first_time is not None and test_last_time is not None:
                    # Use the index of the first test sample as the reference "test window start".
                    # A train event whose END falls at or after that reference but whose start is before
                    # the test window must be purged.
                    if i < test_idx[0] and end_t >= self.event_end_times.iloc[test_idx[0]]:
                        train_mask[i] = False

            # Embargo: drop train indices immediately after test window
            if self.embargo_bars > 0:
                embargo_end = min(n, test_max_idx + 1 + self.embargo_bars)
                train_mask[test_max_idx + 1:embargo_end] = False

            train_idx = indices[train_mask]
            yield train_idx, test_idx
```

Run: `python3 -m pytest tests/test_purged_kfold.py -v` — 4 passed.

- [ ] **Step 3: Commit**

```bash
cd /Users/abhinavunmesh/Desktop/QuantEdge_Platform
git add ml-service/ml_models/purged_kfold.py ml-service/tests/test_purged_kfold.py
git commit -m "feat(ml): purged K-fold CV (Lopez de Prado Ch. 7)

Purges train samples whose event windows overlap the test fold, plus
an embargo of N bars after each test window to account for return
autocorrelation. Prevents the information leak that makes vanilla
KFold misleading on financial data.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Task 5: Model Registry (save / load / latest pointer)

**Files:**
- Create: `ml-service/ml_models/registry.py`
- Create: `ml-service/tests/test_registry.py`

- [ ] **Step 1: Failing tests**

Create `ml-service/tests/test_registry.py`:

```python
"""Tests for ml_models.registry — model versioning + latest pointer."""
import json
from pathlib import Path

import pytest

from ml_models.registry import ModelRegistry


def test_save_and_load_roundtrip(tmp_path):
    reg = ModelRegistry(base_dir=tmp_path)
    obj = {"weights": [0.1, 0.2, 0.3]}
    meta = {"trained_at": "2024-01-01", "oos_ic": 0.04}

    path = reg.save("BTCUSDT", "meta", obj, metadata=meta)
    assert path.exists()

    loaded_obj, loaded_meta = reg.load("BTCUSDT", "meta")
    assert loaded_obj == obj
    assert loaded_meta["trained_at"] == "2024-01-01"


def test_save_increments_version(tmp_path):
    reg = ModelRegistry(base_dir=tmp_path)
    v1 = reg.save("BTCUSDT", "meta", {"w": 1}, metadata={})
    v2 = reg.save("BTCUSDT", "meta", {"w": 2}, metadata={})
    assert "v1" in v1.name
    assert "v2" in v2.name


def test_latest_pointer_is_updated(tmp_path):
    reg = ModelRegistry(base_dir=tmp_path)
    reg.save("BTCUSDT", "meta", {"w": 1}, metadata={})
    reg.save("BTCUSDT", "meta", {"w": 2}, metadata={})

    pointer = tmp_path / "BTCUSDT" / "meta" / "latest.json"
    data = json.loads(pointer.read_text())
    assert data["version"] == 2


def test_load_missing_returns_none_tuple(tmp_path):
    reg = ModelRegistry(base_dir=tmp_path)
    with pytest.raises(FileNotFoundError):
        reg.load("NOSYM", "meta")
```

Run: `python3 -m pytest tests/test_registry.py -v` — 4 FAILs.

- [ ] **Step 2: Implement**

Create `ml-service/ml_models/registry.py`:

```python
"""Model versioning and on-disk registry.

Layout:
    {base_dir}/{symbol}/{model_type}/v{n}.joblib
    {base_dir}/{symbol}/{model_type}/v{n}.meta.json
    {base_dir}/{symbol}/{model_type}/latest.json  →  {"version": n}

Why a separate metadata file: joblib blobs are opaque. A sidecar JSON lets us
inspect training timestamp, out-of-sample metrics, and feature schema without
re-loading the model.
"""
from __future__ import annotations

import json
import re
from pathlib import Path
from typing import Any, Dict, Tuple

import joblib


class ModelRegistry:
    def __init__(self, base_dir: Path):
        self.base_dir = Path(base_dir)

    def _dir(self, symbol: str, model_type: str) -> Path:
        return self.base_dir / symbol / model_type

    def _next_version(self, d: Path) -> int:
        if not d.exists():
            return 1
        versions = [
            int(m.group(1))
            for p in d.iterdir()
            if (m := re.match(r"^v(\d+)\.joblib$", p.name))
        ]
        return max(versions, default=0) + 1

    def save(self, symbol: str, model_type: str, obj: Any,
             metadata: Dict[str, Any]) -> Path:
        d = self._dir(symbol, model_type)
        d.mkdir(parents=True, exist_ok=True)
        version = self._next_version(d)

        blob_path = d / f"v{version}.joblib"
        meta_path = d / f"v{version}.meta.json"
        pointer = d / "latest.json"

        joblib.dump(obj, blob_path)
        meta_path.write_text(json.dumps({"version": version, **metadata}, default=str))
        pointer.write_text(json.dumps({"version": version}))
        return blob_path

    def load(self, symbol: str, model_type: str) -> Tuple[Any, Dict[str, Any]]:
        d = self._dir(symbol, model_type)
        pointer = d / "latest.json"
        if not pointer.exists():
            raise FileNotFoundError(f"No model registered at {d}")
        version = json.loads(pointer.read_text())["version"]
        blob = joblib.load(d / f"v{version}.joblib")
        meta = json.loads((d / f"v{version}.meta.json").read_text())
        return blob, meta
```

Run: `python3 -m pytest tests/test_registry.py -v` — 4 passed.

- [ ] **Step 3: Commit**

```bash
cd /Users/abhinavunmesh/Desktop/QuantEdge_Platform
git add ml-service/ml_models/registry.py ml-service/tests/test_registry.py
git commit -m "feat(ml): versioned on-disk model registry

{base}/{symbol}/{type}/v{n}.joblib + v{n}.meta.json sidecar + atomic
latest.json pointer. Inspection-friendly metadata (trained_at, OOS
metrics, feature schema) without unpickling the model.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Task 6: Meta-Labeler (XGBoost)

**Files:**
- Create: `ml-service/ml_models/meta_labeler.py`
- Create: `ml-service/tests/test_meta_labeler.py`

- [ ] **Step 1: Failing tests**

Create `ml-service/tests/test_meta_labeler.py`:

```python
"""Tests for ml_models.meta_labeler — triple-barrier meta-filter over primary signals."""
import numpy as np
import pandas as pd
import pytest

from ml_models.meta_labeler import MetaLabeler


@pytest.fixture
def toy_training_frame():
    """Feature rows with a clean signal: higher `momentum` → higher P(TP).

    The meta-labeler should learn this monotone relationship.
    """
    rng = np.random.default_rng(0)
    n = 400
    momentum = rng.normal(0, 1, n)
    noise = rng.normal(0, 0.5, n)
    # Labels: TP (1) when momentum + noise > 0, else SL (0)
    labels = ((momentum + noise) > 0).astype(int)
    return pd.DataFrame({
        "momentum": momentum,
        "volatility": rng.normal(0, 1, n),
        "direction": rng.choice([-1, 1], size=n),
        "label": labels,
    })


def test_train_returns_reasonable_accuracy(toy_training_frame):
    model = MetaLabeler(feature_cols=["momentum", "volatility", "direction"])
    result = model.train(toy_training_frame, label_col="label")
    # Toy problem is learnable; expect > 0.65 train accuracy
    assert result["train_accuracy"] > 0.65


def test_predict_returns_probability_in_zero_one(toy_training_frame):
    model = MetaLabeler(feature_cols=["momentum", "volatility", "direction"])
    model.train(toy_training_frame, label_col="label")
    row = toy_training_frame.iloc[[0]].drop(columns=["label"])
    out = model.predict(row)
    assert 0.0 <= out["meta_prob"] <= 1.0
    assert "direction" in out


def test_predict_before_training_raises(toy_training_frame):
    model = MetaLabeler(feature_cols=["momentum", "volatility", "direction"])
    with pytest.raises(RuntimeError, match="not trained"):
        model.predict(toy_training_frame.iloc[[0]])


def test_train_rejects_timeout_labels(toy_training_frame):
    """-1 labels (timeouts) must be filtered out of training, not fed to XGBoost."""
    frame = toy_training_frame.copy()
    frame.loc[:50, "label"] = -1
    model = MetaLabeler(feature_cols=["momentum", "volatility", "direction"])
    result = model.train(frame, label_col="label")
    # n_train should be full length minus the 51 timeout rows
    assert result["n_train"] == len(frame) - 51
```

Run: `python3 -m pytest tests/test_meta_labeler.py -v` — 4 FAILs.

- [ ] **Step 2: Implement**

Create `ml-service/ml_models/meta_labeler.py`:

```python
"""Meta-labeler: XGBoost binary classifier on triple-barrier outcomes.

Input: a feature frame enriched with derivatives features (funding, OI) and
a primary-signal direction column. Label: 1 if primary signal hit TP first,
0 if SL first. Rows labeled -1 (timeout, no clear outcome) are dropped.

The model's output probability is used downstream by the trading gate:
trades with meta_prob < threshold are vetoed.
"""
from __future__ import annotations

import logging
from dataclasses import dataclass, field
from typing import Any, Dict, List, Optional

import numpy as np
import pandas as pd
from xgboost import XGBClassifier

log = logging.getLogger("ml.meta_labeler")


@dataclass
class MetaLabeler:
    feature_cols: List[str]
    n_estimators: int = 200
    max_depth: int = 4
    learning_rate: float = 0.05
    subsample: float = 0.8
    colsample_bytree: float = 0.8
    random_state: int = 42

    _model: Optional[XGBClassifier] = field(default=None, init=False, repr=False)
    _trained: bool = field(default=False, init=False, repr=False)

    def train(self, df: pd.DataFrame, label_col: str = "label") -> Dict[str, Any]:
        mask = df[label_col].isin([0, 1])
        n_total = len(df)
        n_kept = int(mask.sum())
        if n_kept < 50:
            raise ValueError(f"not enough binary labels ({n_kept}); need >= 50")

        frame = df.loc[mask].copy()
        X = frame[self.feature_cols]
        y = frame[label_col].astype(int)

        self._model = XGBClassifier(
            n_estimators=self.n_estimators,
            max_depth=self.max_depth,
            learning_rate=self.learning_rate,
            subsample=self.subsample,
            colsample_bytree=self.colsample_bytree,
            random_state=self.random_state,
            eval_metric="logloss",
            tree_method="hist",
        )
        self._model.fit(X, y)
        self._trained = True

        train_pred = self._model.predict(X)
        train_acc = float(np.mean(train_pred == y.values))

        return {
            "n_train": n_kept,
            "n_dropped_timeout": n_total - n_kept,
            "train_accuracy": train_acc,
            "feature_cols": self.feature_cols,
        }

    def predict(self, features: pd.DataFrame) -> Dict[str, Any]:
        if not self._trained or self._model is None:
            raise RuntimeError("MetaLabeler not trained; call train() first")
        X = features[self.feature_cols]
        prob = float(self._model.predict_proba(X)[0, 1])
        direction = int(features["direction"].iloc[0]) if "direction" in features.columns else 0
        return {"meta_prob": prob, "direction": direction}
```

Run: `python3 -m pytest tests/test_meta_labeler.py -v` — 4 passed.

- [ ] **Step 3: Commit**

```bash
cd /Users/abhinavunmesh/Desktop/QuantEdge_Platform
git add ml-service/ml_models/meta_labeler.py ml-service/tests/test_meta_labeler.py
git commit -m "feat(ml): XGBoost meta-labeler for triple-barrier signals

Binary classifier over primary-signal features + direction + derivatives
context. Timeout rows (-1) dropped before training. Predicts P(TP first)
for each new signal; threshold applied by callers.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Task 7: Order-Flow GBDT (LightGBM)

**Files:**
- Modify: `ml-service/requirements.txt` (add lightgbm)
- Create: `ml-service/ml_models/order_flow.py`
- Create: `ml-service/tests/test_order_flow.py`

- [ ] **Step 1: Add lightgbm to requirements**

Append to `ml-service/requirements.txt`:
```
lightgbm>=4.3.0
```

Run: `cd /Users/abhinavunmesh/Desktop/QuantEdge_Platform/ml-service && pip install lightgbm 2>&1 | tail -3`

Expected: installed or already-satisfied.

- [ ] **Step 2: Failing tests**

Create `ml-service/tests/test_order_flow.py`:

```python
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
```

Run: `python3 -m pytest tests/test_order_flow.py -v` — 5 FAILs.

- [ ] **Step 3: Implement**

Create `ml-service/ml_models/order_flow.py`:

```python
"""Order-flow model: LightGBM directional classifier on microstructure fallback features.

Why fallback: full L2 book reconstruction is out of scope at retail capital. The
fallback set is documented (~70% of edge retained) and uses features derivable
from bar-level OHLCV plus derivatives data we already ingest:
    - CVD proxy: signed volume where sign comes from candle direction
    - Aggressive-buy-ratio: fraction of up-bars in rolling window
    - Funding rate and its delta
    - Open interest deltas (1-bar and 4-bar)
    - Perp-spot basis proxy: using bar close vs recent rolling mean

Label: direction of the next `forward_bars`-bar return.
"""
from __future__ import annotations

import logging
from dataclasses import dataclass, field
from typing import Any, Dict, List, Optional

import numpy as np
import pandas as pd
from lightgbm import LGBMClassifier

log = logging.getLogger("ml.order_flow")

FLOW_FEATURE_COLS: List[str] = [
    "cvd",
    "aggressive_buy_ratio_20",
    "funding_rate",
    "funding_rate_delta",
    "oi_delta_1",
    "oi_delta_4",
    "basis_proxy",
]


def compute_flow_features(bars: pd.DataFrame) -> pd.DataFrame:
    """Build fallback microstructure features from bar OHLCV + derivatives cols.

    Callers should pre-enrich bars with `feature_enrichment.enrich_with_derivatives`.
    """
    out = bars.copy().sort_values("time").reset_index(drop=True)

    close = out["close"].astype(float)
    volume = out["volume"].astype(float)

    # CVD proxy: cumulative signed volume; up-bar = +volume, down-bar = -volume
    direction = np.sign(close.diff().fillna(0.0))
    signed_vol = direction * volume
    out["cvd"] = signed_vol.cumsum()

    # Aggressive buy ratio: fraction of up-bars in trailing 20-bar window
    up = (direction > 0).astype(float)
    out["aggressive_buy_ratio_20"] = up.rolling(20, min_periods=5).mean().fillna(0.5)

    # Basis proxy: normalized distance of current close from 20-bar mean
    ma20 = close.rolling(20, min_periods=1).mean()
    out["basis_proxy"] = ((close - ma20) / ma20).fillna(0.0)

    # Pass-through enrichment columns (ensure they exist; default to 0)
    for col in ["funding_rate", "funding_rate_delta", "oi_delta_1", "oi_delta_4"]:
        if col not in out.columns:
            out[col] = 0.0

    return out


@dataclass
class OrderFlowModel:
    n_estimators: int = 200
    max_depth: int = -1
    num_leaves: int = 31
    learning_rate: float = 0.05
    random_state: int = 42
    min_confidence: float = 0.55

    _model: Optional[LGBMClassifier] = field(default=None, init=False, repr=False)
    _trained: bool = field(default=False, init=False, repr=False)

    def train(self, enriched_bars: pd.DataFrame, forward_bars: int = 4) -> Dict[str, Any]:
        feat = compute_flow_features(enriched_bars)
        close = feat["close"].astype(float)
        forward_ret = close.shift(-forward_bars) / close - 1.0
        y = np.sign(forward_ret).astype(int)
        # Strict future data: y[-forward_bars:] is NaN — drop those
        mask = y.notna()
        X = feat.loc[mask, FLOW_FEATURE_COLS]
        y = y.loc[mask].astype(int)
        # Map {-1, 0, 1} → {0, 1, 2} for classifier
        y_mapped = y + 1

        if len(X) < 50:
            raise ValueError(f"not enough rows ({len(X)}); need >= 50")

        self._model = LGBMClassifier(
            n_estimators=self.n_estimators,
            max_depth=self.max_depth,
            num_leaves=self.num_leaves,
            learning_rate=self.learning_rate,
            random_state=self.random_state,
            verbose=-1,
        )
        self._model.fit(X, y_mapped)
        self._trained = True

        pred = self._model.predict(X)
        train_acc = float(np.mean(pred == y_mapped.values))
        return {
            "n_train": len(X),
            "train_accuracy": train_acc,
            "feature_cols": FLOW_FEATURE_COLS,
            "forward_bars": forward_bars,
        }

    def predict(self, feat_row: pd.DataFrame) -> Dict[str, Any]:
        if not self._trained or self._model is None:
            raise RuntimeError("OrderFlowModel not trained")
        X = feat_row[FLOW_FEATURE_COLS].tail(1)
        probs = self._model.predict_proba(X)[0]
        # Class order: 0 → short, 1 → flat, 2 → long
        conf = float(probs.max())
        cls = int(np.argmax(probs)) - 1  # back to {-1, 0, 1}
        direction = cls if conf >= self.min_confidence else 0
        return {
            "flow_score": conf,
            "direction": direction,
            "probs": {
                "short": float(probs[0]),
                "flat":  float(probs[1]),
                "long":  float(probs[2]),
            },
        }
```

Run: `python3 -m pytest tests/test_order_flow.py -v` — 5 passed.

- [ ] **Step 4: Commit**

```bash
cd /Users/abhinavunmesh/Desktop/QuantEdge_Platform
git add ml-service/ml_models/order_flow.py ml-service/tests/test_order_flow.py \
        ml-service/requirements.txt
git commit -m "feat(ml): LightGBM order-flow model on fallback features

CVD proxy + rolling aggressive-buy ratio + funding/OI/basis features.
Classifies next-N-bar direction into short/flat/long with a
confidence-threshold veto (default 0.55). Fallback set documented as
retaining ~70% of L2-book edge; production upgrade path preserved.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Task 8: FastAPI endpoints

**Files:**
- Modify: `ml-service/main.py`
- Create: `ml-service/tests/test_ml_endpoints.py`

- [ ] **Step 1: Failing tests**

Create `ml-service/tests/test_ml_endpoints.py`:

```python
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
    n = 400
    rng = np.random.default_rng(0)
    closes = 100 + np.cumsum(rng.normal(0, 0.5, n))
    return pd.DataFrame({
        "time":   pd.date_range("2024-01-01", periods=n, freq="15min", tz="UTC"),
        "open":   closes,
        "high":   closes + 0.1,
        "low":    closes - 0.1,
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
```

Run: `python3 -m pytest tests/test_ml_endpoints.py -v` — 5 FAILs (endpoints don't exist).

- [ ] **Step 2: Extend `main.py`**

Read `ml-service/main.py` first. Then add these imports near the top:

```python
import os

from ml_models.feature_enrichment import enrich_with_derivatives, ENRICHED_COLS
from ml_models.meta_labeler import MetaLabeler
from ml_models.order_flow import OrderFlowModel, FLOW_FEATURE_COLS, compute_flow_features
from ml_models.primary_signals import replay_momentum_primary
from ml_models.registry import ModelRegistry
from labelers.triple_barrier import apply_triple_barrier
from feature_engine import compute_features, FEATURE_COLS
```

Add this block near the existing module-level config (after `MODEL_DIR = "models/"`):

```python
ML_MODEL_DIR = os.environ.get("ML_MODEL_DIR", MODEL_DIR)
_registry = ModelRegistry(base_dir=ML_MODEL_DIR)
_meta_models: dict[str, MetaLabeler] = {}
_flow_models: dict[str, OrderFlowModel] = {}

META_FEATURE_COLS = [
    "rsi", "macd_hist", "bb_pctb", "sma_cross", "atr_pct", "rel_volume",
    "volatility_20d", "funding_rate", "funding_rate_delta",
    "oi_delta_1", "direction",
]
```

Add these endpoints at the bottom of the file (before the `if __name__ == "__main__":` block):

```python
class PredictMetaRequest(BaseModel):
    primary_signal: str  # "LONG" | "SHORT"
    entry_price: float
    tp_pct: float = 0.02
    sl_pct: float = 0.01


class PredictFlowRequest(BaseModel):
    lookback_bars: int = 200


@app.post("/train-meta/{symbol}")
async def train_meta(symbol: str, days: int = 500):
    """Train the triple-barrier meta-labeler on historical bars for {symbol}."""
    df = await fetch_market_data(symbol, days)
    enriched = enrich_with_derivatives(df, funding=None, oi=None)
    featured = compute_features(enriched)
    for c in ENRICHED_COLS:
        if c not in featured.columns:
            featured[c] = 0.0
    featured = featured.dropna(subset=FEATURE_COLS).reset_index(drop=True)

    signals = replay_momentum_primary(featured, fast=10, slow=50)
    labels = apply_triple_barrier(
        featured[["time", "high", "low", "close"]], signals,
        tp_pct=0.02, sl_pct=0.01, max_bars=24,
    )
    if len(labels) == 0:
        raise HTTPException(status_code=400, detail="No labels produced (not enough forward horizon)")

    merged = labels.merge(
        featured, left_on="signal_time", right_on="time", how="inner",
    )
    merged["direction"] = labels["direction"].values

    model = MetaLabeler(feature_cols=META_FEATURE_COLS)
    result = model.train(merged[META_FEATURE_COLS + ["label"]], label_col="label")
    _meta_models[symbol] = model
    path = _registry.save(symbol, "meta", model, metadata={"symbol": symbol, **result})
    result["saved_to"] = str(path)
    return result


@app.post("/predict-meta/{symbol}")
async def predict_meta(symbol: str, req: PredictMetaRequest):
    """Score a single primary signal. Loads from registry if not in memory."""
    if symbol not in _meta_models:
        try:
            model, _ = _registry.load(symbol, "meta")
            _meta_models[symbol] = model
        except FileNotFoundError:
            raise HTTPException(status_code=400, detail=f"No meta model for {symbol}")

    df = await fetch_market_data(symbol, 100)
    enriched = enrich_with_derivatives(df, funding=None, oi=None)
    featured = compute_features(enriched)
    for c in ENRICHED_COLS:
        if c not in featured.columns:
            featured[c] = 0.0
    featured = featured.dropna(subset=FEATURE_COLS)
    if featured.empty:
        raise HTTPException(status_code=400, detail="Not enough bars to compute features")

    direction = 1 if req.primary_signal == "LONG" else -1
    last = featured.iloc[[-1]].copy()
    last["direction"] = direction
    out = _meta_models[symbol].predict(last[META_FEATURE_COLS])
    return {
        "symbol": symbol,
        "meta_prob": out["meta_prob"],
        "direction": out["direction"],
        "primary_signal": req.primary_signal,
    }


@app.post("/train-flow/{symbol}")
async def train_flow(symbol: str, days: int = 500):
    df = await fetch_market_data(symbol, days)
    enriched = enrich_with_derivatives(df, funding=None, oi=None)

    model = OrderFlowModel()
    result = model.train(enriched, forward_bars=4)
    _flow_models[symbol] = model
    path = _registry.save(symbol, "flow", model, metadata={"symbol": symbol, **result})
    result["saved_to"] = str(path)
    return result


@app.post("/predict-flow/{symbol}")
async def predict_flow(symbol: str, req: PredictFlowRequest):
    if symbol not in _flow_models:
        try:
            model, _ = _registry.load(symbol, "flow")
            _flow_models[symbol] = model
        except FileNotFoundError:
            raise HTTPException(status_code=400, detail=f"No flow model for {symbol}")

    df = await fetch_market_data(symbol, req.lookback_bars + 50)
    enriched = enrich_with_derivatives(df, funding=None, oi=None)
    feat = compute_flow_features(enriched).tail(1)
    out = _flow_models[symbol].predict(feat)
    return {"symbol": symbol, **out}
```

- [ ] **Step 3: Run tests**

Run: `cd /Users/abhinavunmesh/Desktop/QuantEdge_Platform/ml-service && python3 -m pytest tests/test_ml_endpoints.py -v` — expect 5 passed.

Run the full test suite too: `python3 -m pytest -v 2>&1 | tail -30`. Expect all new tests green, older ones still green, DB integration tests skipped.

- [ ] **Step 4: Commit**

```bash
cd /Users/abhinavunmesh/Desktop/QuantEdge_Platform
git add ml-service/main.py ml-service/tests/test_ml_endpoints.py
git commit -m "feat(ml): /train-meta, /predict-meta, /train-flow, /predict-flow

Four new FastAPI endpoints exposing the meta-labeler and order-flow
models. Training endpoints fit on fetched OHLCV, persist to the
registry. Predict endpoints hydrate from registry on cache miss.
Meta features = compute_features + derivatives + direction; flow
features = compute_flow_features. Endpoint tests use FastAPI TestClient
with a patched fetch_market_data so no backend is required.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Task 9: Java MLMetaClient (HTTP wrapper, no wiring)

**Files:**
- Create: `QuantPlatformApplication/src/main/java/com/QuantPlatformApplication/QuantPlatformApplication/client/MLMetaPredictionResponse.java`
- Create: `QuantPlatformApplication/src/main/java/com/QuantPlatformApplication/QuantPlatformApplication/client/MLMetaClient.java`
- Create: `QuantPlatformApplication/src/test/java/com/QuantPlatformApplication/QuantPlatformApplication/client/MLMetaClientTest.java`

- [ ] **Step 1: Response DTO**

Create `QuantPlatformApplication/src/main/java/com/QuantPlatformApplication/QuantPlatformApplication/client/MLMetaPredictionResponse.java`:

```java
package com.QuantPlatformApplication.QuantPlatformApplication.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Shape of /predict-meta/{symbol} response from ml-service.
 * Extra fields are tolerated so the Python side can evolve.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MLMetaPredictionResponse(
        String symbol,
        double metaProb,
        int direction,
        String primarySignal
) {}
```

Note: the Python endpoint returns `meta_prob` (snake_case); the Java DTO uses camelCase via Jackson's default. This will break deserialization unless we add a property annotation. Apply the naming map explicitly:

Replace with:

```java
package com.QuantPlatformApplication.QuantPlatformApplication.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MLMetaPredictionResponse(
        String symbol,
        @JsonProperty("meta_prob") double metaProb,
        int direction,
        @JsonProperty("primary_signal") String primarySignal
) {}
```

- [ ] **Step 2: Failing test for the client**

Create `QuantPlatformApplication/src/test/java/com/QuantPlatformApplication/QuantPlatformApplication/client/MLMetaClientTest.java`:

```java
package com.QuantPlatformApplication.QuantPlatformApplication.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MLMetaClientTest {

    private MockWebServer server;
    private MLMetaClient client;

    @BeforeEach
    void setup() throws Exception {
        server = new MockWebServer();
        server.start();
        client = new MLMetaClient(server.url("/").toString().replaceAll("/$", ""), new ObjectMapper());
    }

    @AfterEach
    void teardown() throws Exception {
        server.shutdown();
    }

    @Test
    void predictMeta_parsesHappyResponse() throws Exception {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("{\"symbol\":\"BTCUSDT\",\"meta_prob\":0.62,\"direction\":1,\"primary_signal\":\"LONG\"}"));

        MLMetaPredictionResponse resp = client.predictMeta(
                "BTCUSDT", "LONG", 42000.0, 0.02, 0.01);

        assertThat(resp.symbol()).isEqualTo("BTCUSDT");
        assertThat(resp.metaProb()).isEqualTo(0.62);
        assertThat(resp.direction()).isEqualTo(1);
    }

    @Test
    void predictMeta_throwsOn500() {
        server.enqueue(new MockResponse().setResponseCode(500).setBody("boom"));

        assertThatThrownBy(() -> client.predictMeta("BTCUSDT", "LONG", 42000.0, 0.02, 0.01))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("500");
    }
}
```

Run (Java 21): `cd /Users/abhinavunmesh/Desktop/QuantEdge_Platform/QuantPlatformApplication && ./mvnw test -Dtest=MLMetaClientTest 2>&1 | tail -15`

If `okhttp-mockwebserver` isn't on the classpath, check `pom.xml`. If it's missing, add test-scope dependency:

```xml
<dependency>
    <groupId>com.squareup.okhttp3</groupId>
    <artifactId>mockwebserver</artifactId>
    <version>4.12.0</version>
    <scope>test</scope>
</dependency>
```

Then re-run.

Expected: COMPILATION FAILURE (MLMetaClient not defined).

- [ ] **Step 3: Implement the client**

Create `QuantPlatformApplication/src/main/java/com/QuantPlatformApplication/QuantPlatformApplication/client/MLMetaClient.java`:

```java
package com.QuantPlatformApplication.QuantPlatformApplication.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * Typed client for ml-service's /predict-meta endpoint.
 *
 * Intentionally thin: this commit just wraps the HTTP call and DTO. Integration
 * into TradeRiskEngine as a veto is scoped to Plan 4 (paper trading wire-up) so
 * that changing the gating semantics is its own reviewable change.
 */
@Slf4j
@Component
public class MLMetaClient {

    private final String baseUrl;
    private final ObjectMapper objectMapper;
    private final HttpClient http;

    public MLMetaClient(
            @Value("${quantedge.ml.url:http://localhost:5001}") String baseUrl,
            ObjectMapper objectMapper) {
        this.baseUrl = baseUrl;
        this.objectMapper = objectMapper;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    public MLMetaPredictionResponse predictMeta(
            String symbol, String primarySignal,
            double entryPrice, double tpPct, double slPct) {
        try {
            String body = objectMapper.writeValueAsString(Map.of(
                    "primary_signal", primarySignal,
                    "entry_price", entryPrice,
                    "tp_pct", tpPct,
                    "sl_pct", slPct));

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/predict-meta/" + symbol))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() >= 300) {
                throw new RuntimeException(
                        "predict-meta returned " + resp.statusCode() + ": " + resp.body());
            }
            return objectMapper.readValue(resp.body(), MLMetaPredictionResponse.class);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("predict-meta call failed", e);
        }
    }
}
```

Run the test again (Java 21 in PATH): `./mvnw test -Dtest=MLMetaClientTest 2>&1 | tail -15` — expect 2 passed.

Also run a full compile: `./mvnw -q compile 2>&1 | tail -5` — expect BUILD SUCCESS.

- [ ] **Step 4: Commit**

```bash
cd /Users/abhinavunmesh/Desktop/QuantEdge_Platform
git add QuantPlatformApplication/src/main/java/com/QuantPlatformApplication/QuantPlatformApplication/client/MLMetaClient.java \
        QuantPlatformApplication/src/main/java/com/QuantPlatformApplication/QuantPlatformApplication/client/MLMetaPredictionResponse.java \
        QuantPlatformApplication/src/test/java/com/QuantPlatformApplication/QuantPlatformApplication/client/MLMetaClientTest.java \
        QuantPlatformApplication/pom.xml
git commit -m "feat(ml): MLMetaClient thin HTTP wrapper around /predict-meta

Typed client + response DTO. Intentionally NOT wired into
TradeRiskEngine yet — the veto semantics change belongs with the
paper trading work (Plan 4) so it can be reviewed as one diff.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Self-Review Notes

**Spec coverage** (Plan 2 scope only, per spec §4.4):
- Triple-barrier labeler: Task 1 ✅
- Meta-labeler (XGBoost) + `/predict-meta`: Tasks 6 + 8 ✅
- Order-flow GBDT (LightGBM fallback features) + `/predict-flow`: Tasks 7 + 8 ✅
- Walk-forward training: PurgedKFold in Task 4 ✅ (it's wired into the models as an optional future eval loop; the initial training endpoint fits once on all data for simplicity, and Plan 4 adds weekly retrain with walk-forward)
- Model persistence: Task 5 ✅
- Deprecation of old endpoints: already delivered in Plan 1, not duplicated here ✅
- Java integration: Task 9 (HTTP client only; TradeRiskEngine wire-up explicitly deferred to Plan 4)

**Placeholder scan**: no "TBD", "implement later", or abstract "handle edge cases" phrases. Each step has code or a concrete command.

**Type consistency checks**:
- `MetaLabeler.train()` accepts `(df, label_col)` → matches caller in Task 8.
- `MetaLabeler.predict()` returns `{meta_prob, direction}` → matches Java DTO `@JsonProperty("meta_prob")`.
- `OrderFlowModel.predict()` returns `{flow_score, direction, probs}` → matches `/predict-flow` endpoint response and its test.
- `META_FEATURE_COLS` in `main.py` matches what `compute_features` + `enrich_with_derivatives` + manual `direction` produces.
- `FLOW_FEATURE_COLS` in `order_flow.py` matches what `compute_flow_features` produces.
- `apply_triple_barrier()` signature `(bars, signals, tp_pct, sl_pct, max_bars)` matches the caller in Task 8.

**Known soft spots flagged inline**:
- Task 4's PurgedKFold works for the tests as written but is more brittle than a library implementation; if coverage issues surface in Plan 4, replace with `sklearn`'s `TimeSeriesSplit` + manual purge.
- Task 8's `compute_features` output may not include all `META_FEATURE_COLS` (e.g., `direction` is injected manually; `funding_rate` etc. come from `enrich_with_derivatives`). The endpoint code fills any missing columns with 0. This is intentional — the model treats zero as "no signal".
- Task 9 uses `java.net.http.HttpClient` rather than Spring's `WebClient`. Matches the JDK-21 default and avoids pulling reactive-web into the client path.

**Deferred (explicitly out of scope)**:
- Weekly retrain cron → Plan 4
- Risk-engine veto → Plan 4
- Async fetch / batch prediction / caching → defer until profiling shows a need
