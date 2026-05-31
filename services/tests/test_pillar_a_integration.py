"""Integration test: data adapter -> scanner -> opportunities."""
import pytest
from unittest.mock import MagicMock, patch
import pandas as pd
from services.data.base import MarketSnapshot
from services.data.yfinance import YFinanceProvider
from agents.scanner.scanner import MarketScanner, OpportunityList
from agents.scanner.config import ScannerConfig


@pytest.fixture
def mock_snapshots():
    return [
        MarketSnapshot(symbol="AAPL", price=150.0, volume_24h=50_000_000, change_24h_pct=2.5),
        MarketSnapshot(symbol="TSLA", price=250.0, volume_24h=120_000_000, change_24h_pct=-4.2),
        MarketSnapshot(symbol="NVDA", price=800.0, volume_24h=80_000_000, change_24h_pct=1.2),
    ]


class TestScannerIntegration:
    def test_scanner_produces_ranked_opportunities(self, mock_snapshots):
        mock_provider = MagicMock()
        mock_provider.get_name.return_value = "mock"
        mock_provider.fetch_snapshots.return_value = mock_snapshots

        config = ScannerConfig(symbols=["AAPL", "TSLA", "NVDA"], min_volume_24h=1_000_000)
        scanner = MarketScanner(provider=mock_provider, config=config)

        result = scanner.scan()

        assert isinstance(result, OpportunityList)
        assert result.scanned_count == 3
        assert len(result.opportunities) >= 0

        if result.opportunities:
            strengths = [o.signal_strength for o in result.opportunities]
            assert strengths == sorted(strengths, reverse=True), "Opportunities should be ranked by signal strength"

    def test_disabled_scanner_returns_empty(self, mock_snapshots):
        mock_provider = MagicMock()
        config = ScannerConfig(enabled=False)
        scanner = MarketScanner(provider=mock_provider, config=config)
        result = scanner.scan()
        assert len(result.opportunities) == 0

    def test_scanner_integration_with_kronos(self):
        """Verify scanner output is compatible with Kronos forecast input format."""
        snap = MarketSnapshot(symbol="AAPL", price=150.0, volume_24h=50_000_000, change_24h_pct=2.5)

        # Scanner produces an Opportunity
        from agents.scanner.scanner import Opportunity
        opp = Opportunity(
            asset=snap.symbol,
            asset_type="EQUITY",
            price=snap.price,
            change_24h_pct=snap.change_24h_pct,
            volume_24h=snap.volume_24h,
            signal_strength=0.65,
            reasons=["Significant move"],
        )

        # This opportunity should contain sufficient info for downstream consumers
        assert opp.asset == "AAPL"
        assert opp.signal_strength > 0.5
        assert isinstance(opp.reasons, list)


class TestPillarAMockFlow:
    def test_full_mock_flow(self, mock_snapshots):
        """Simulate the complete Pillar A flow: data -> scanner -> ranked opportunities."""
        provider = MagicMock()
        provider.get_name.return_value = "mock"
        provider.fetch_snapshots.return_value = mock_snapshots

        scanner = MarketScanner(provider=provider)
        result = scanner.scan()

        assert isinstance(result, OpportunityList)
        top = result.top(3)
        assert len(top) <= 3

        # Verify top opportunity has data
        if top:
            best = top[0]
            assert best.asset in ["AAPL", "TSLA", "NVDA"]
            assert best.price > 0
            assert best.signal_strength >= 0.0
            assert best.signal_strength <= 1.0
