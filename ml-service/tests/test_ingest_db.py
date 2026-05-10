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
