"""Tests for market data providers."""
import pandas as pd
import pytest
from services.data.base import MarketDataProvider, MarketSnapshot
from services.data.yfinance import YFinanceProvider


class TestMarketSnapshot:
    def test_snapshot_creation(self):
        snap = MarketSnapshot(symbol="AAPL", price=150.0, volume_24h=50000000.0)
        assert snap.symbol == "AAPL"
        assert snap.price == 150.0
        assert snap.change_24h_pct == 0.0

    def test_timestamp_auto_set(self):
        snap = MarketSnapshot(symbol="TEST", price=10.0)
        assert snap.timestamp is not None


class TestYFinanceProvider:
    def test_get_name(self):
        p = YFinanceProvider()
        assert p.get_name() == "yfinance"

    def test_import_handling(self):
        p = YFinanceProvider()
        # Should not crash on init even if yfinance not installed
        assert p.get_name() == "yfinance"

    def test_ohlcv_empty_if_not_installed(self):
        p = YFinanceProvider()
        if p._yf is None:
            with pytest.raises(ImportError):
                p.fetch_ohlcv("AAPL")
