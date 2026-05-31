"""Tests for Market Scanner agent."""
import pytest
from services.data.base import MarketSnapshot
from agents.scanner.filters import filter_liquidity, filter_spread, filter_unusual_volume, filter_significant_move
from agents.scanner.scanner import Opportunity, OpportunityList
from agents.scanner.config import ScannerConfig


@pytest.fixture
def snapshots():
    return [
        MarketSnapshot(symbol="AAPL", price=150.0, volume_24h=50_000_000, change_24h_pct=2.5),
        MarketSnapshot(symbol="TSLA", price=250.0, volume_24h=120_000_000, change_24h_pct=-4.2),
        MarketSnapshot(symbol="PENNY", price=0.50, volume_24h=50_000, change_24h_pct=8.0),
        MarketSnapshot(symbol="NVDA", price=800.0, volume_24h=80_000_000, change_24h_pct=1.2),
    ]


class TestFilters:
    def test_filter_liquidity(self, snapshots):
        result = filter_liquidity(snapshots, min_volume_24h=1_000_000)
        symbols = {s.symbol for s in result}
        assert "PENNY" not in symbols
        assert "AAPL" in symbols

    def test_filter_liquidity_all_pass(self, snapshots):
        result = filter_liquidity(snapshots, min_volume_24h=0)
        assert len(result) == 4

    def test_filter_spread(self, snapshots):
        result = filter_spread(snapshots, max_spread_pct=1.0)
        assert len(result) == 4

    def test_filter_unusual_volume(self, snapshots):
        history = {"AAPL": [40_000_000] * 7, "TSLA": [120_000_000] * 7}
        result = filter_unusual_volume(snapshots, history, sigma=2.0)
        symbols = {s.symbol for s in result}
        assert "AAPL" in symbols

    def test_filter_significant_move(self, snapshots):
        result = filter_significant_move(snapshots, min_change_pct=3.0)
        symbols = {s.symbol for s in result}
        assert "TSLA" in symbols
        assert "PENNY" in symbols
        assert "AAPL" not in symbols


class TestOpportunity:
    def test_opportunity_list_sorting(self):
        ops = OpportunityList(opportunities=[
            Opportunity(asset="A", asset_type="EQUITY", price=100, change_24h_pct=1.0, volume_24h=1e6, signal_strength=0.3),
            Opportunity(asset="B", asset_type="EQUITY", price=200, change_24h_pct=2.0, volume_24h=2e6, signal_strength=0.8),
            Opportunity(asset="C", asset_type="EQUITY", price=300, change_24h_pct=3.0, volume_24h=3e6, signal_strength=0.5),
        ])
        top = ops.top(2)
        assert len(top) == 2
        assert top[0].asset == "B"
        assert top[1].asset == "C"


class TestScannerConfig:
    def test_default_symbols(self):
        cfg = ScannerConfig()
        assert "AAPL" in cfg.symbols
        assert "MSFT" in cfg.symbols
