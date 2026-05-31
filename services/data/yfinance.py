"""Yahoo Finance data provider for US equities."""
from __future__ import annotations

import logging
from datetime import datetime
from typing import List, Optional

import pandas as pd

from services.data.base import MarketDataProvider, MarketSnapshot

_log = logging.getLogger(__name__)


class YFinanceProvider(MarketDataProvider):
    def __init__(self):
        self._yf = None
        self._import_error: Optional[str] = None
        self._try_import()

    def _try_import(self):
        try:
            import yfinance as yf
            self._yf = yf
        except ImportError:
            self._import_error = "yfinance not installed: pip install yfinance"

    def get_name(self) -> str:
        return "yfinance"

    def fetch_ohlcv(self, symbol: str, interval: str = "1h", days: int = 30) -> pd.DataFrame:
        if self._yf is None:
            raise ImportError(self._import_error)

        ticker = self._yf.Ticker(symbol)
        df = ticker.history(period=f"{days}d", interval=interval)

        if df.empty:
            _log.warning("No data for %s", symbol)
            return pd.DataFrame()

        df = df.rename(columns={
            "Open": "open", "High": "high",
            "Low": "low", "Close": "close",
            "Volume": "volume",
        })

        cols = ["open", "high", "low", "close", "volume"]
        available = [c for c in cols if c in df.columns]
        return df[available]

    def fetch_snapshot(self, symbol: str) -> Optional[MarketSnapshot]:
        if self._yf is None:
            raise ImportError(self._import_error)

        try:
            ticker = self._yf.Ticker(symbol)
            info = ticker.info or {}
            hist = ticker.history(period="5d")
            price = info.get("regularMarketPrice") or info.get("currentPrice") or 0.0

            if not hist.empty:
                last = hist.iloc[-1]
                price = price or last["Close"]
                high_24h = hist["High"].max()
                low_24h = hist["Low"].min()
                volume_24h = hist["Volume"].sum()
            else:
                high_24h = price
                low_24h = price
                volume_24h = 0.0

            change = info.get("regularMarketChangePercent") or 0.0

            price = float(price) if price else 0.0

            return MarketSnapshot(
                symbol=symbol,
                price=price,
                volume_24h=float(volume_24h),
                change_24h_pct=float(change),
                high_24h=float(high_24h),
                low_24h=float(low_24h),
                spread_pct=0.0,
            )
        except Exception as e:
            _log.warning("Failed to fetch snapshot for %s: %s", symbol, e)
            return None

    def fetch_snapshots(self, symbols: List[str]) -> List[MarketSnapshot]:
        results = []
        for sym in symbols:
            snapshot = self.fetch_snapshot(sym)
            if snapshot:
                results.append(snapshot)
        return results
