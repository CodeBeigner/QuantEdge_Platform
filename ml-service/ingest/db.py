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
        cur.execute("DROP TABLE IF EXISTS staging_market_data")
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
        cur.execute("DROP TABLE IF EXISTS staging_funding")
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
