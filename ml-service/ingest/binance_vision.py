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
