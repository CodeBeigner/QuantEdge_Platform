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


def test_upsert_market_data_multiple_calls_same_transaction():
    """Regression: calling upsert_market_data multiple times in the same transaction must not fail.

    Before the fix, temp tables with ON COMMIT DROP persisted for the entire transaction,
    so the second call would fail with "relation staging_market_data already exists".
    Adding DROP TABLE IF EXISTS before CREATE TEMP TABLE makes the function re-entrant.
    """
    from ingest.db import connect, upsert_market_data

    t1 = datetime(2024, 1, 1, 0, 0, tzinfo=timezone.utc)
    t2 = datetime(2024, 1, 1, 1, 0, tzinfo=timezone.utc)
    df1 = pd.DataFrame({
        "time": [t1],
        "symbol": ["TESTPAIR2"],
        "timeframe": ["15m"],
        "open":   [100.0],
        "high":   [101.0],
        "low":    [99.0],
        "close":  [100.5],
        "volume": [1234.0],
    })
    df2 = pd.DataFrame({
        "time": [t2],
        "symbol": ["TESTPAIR2"],
        "timeframe": ["15m"],
        "open":   [102.0],
        "high":   [103.0],
        "low":    [101.0],
        "close":  [102.5],
        "volume": [5678.0],
    })
    with connect() as conn:
        n1 = upsert_market_data(conn, df1)
        n2 = upsert_market_data(conn, df2)  # Must not fail with "relation already exists"
        assert n1 == 1
        assert n2 == 1
        with conn.cursor() as cur:
            cur.execute(
                "SELECT COUNT(*) FROM market_data WHERE symbol = %s", ("TESTPAIR2",)
            )
            assert cur.fetchone()[0] == 2
            cur.execute("DELETE FROM market_data WHERE symbol = %s", ("TESTPAIR2",))
        conn.commit()
