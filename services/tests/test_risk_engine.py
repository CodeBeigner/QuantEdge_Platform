"""Tests for the risk engine's 7 sequential checks."""
import pytest
from services.risk.config import Direction, PredictionSignal, PortfolioState, RiskConfig
from services.risk.engine import validate_order


class TestRiskEngineEdgeCheck:
    def test_strong_signal_passes(self, risk_config, bull_signal, healthy_portfolio):
        result = validate_order(bull_signal, healthy_portfolio, risk_config)
        assert result.passed
        assert result.sized_order is not None
        assert "edge_check" not in result.failures

    def test_weak_confidence_fails(self, risk_config, weak_signal, healthy_portfolio):
        result = validate_order(weak_signal, healthy_portfolio, risk_config)
        assert not result.passed
        assert any("confidence" in f.lower() for f in result.failures)


class TestRiskEngineKelly:
    def test_kelly_sizes_position(self, risk_config, bull_signal, healthy_portfolio):
        result = validate_order(bull_signal, healthy_portfolio, risk_config)
        assert result.passed
        assert result.applied_size > 0
        assert result.applied_fraction == 0.25

    def test_negative_edge_blocks_trade(self, risk_config, healthy_portfolio):
        bad_signal = PredictionSignal(
            asset="DOG",
            direction=Direction.LONG,
            probability=0.30,
            confidence=0.75,
            entry_price=10.0,
        )
        result = validate_order(bad_signal, healthy_portfolio, risk_config)
        assert not result.passed
        assert result.sized_order is None


class TestRiskEnginePositionLimits:
    def test_size_within_5pct_passes(self, risk_config, bull_signal, healthy_portfolio):
        result = validate_order(bull_signal, healthy_portfolio, risk_config)
        assert result.passed
        assert result.sized_order is not None
        assert result.sized_order.size_dollars <= 0.05 * healthy_portfolio.nav

    def test_oversized_clamped(self, healthy_portfolio):
        tight_config = RiskConfig(
            min_confidence_threshold=0.55,
            kelly_fraction=0.25,
            max_position_pct=0.01,
            max_total_exposure=3.0,
            max_drawdown=0.08,
            daily_loss_limit=5000.0,
            daily_var_limit=10000.0,
            slippage_threshold=0.02,
        )
        huge_signal = PredictionSignal(
            asset="BIG",
            direction=Direction.LONG,
            probability=0.99,
            confidence=0.99,
            entry_price=1000.0,
        )
        result = validate_order(huge_signal, healthy_portfolio, tight_config)
        assert result.passed
        assert result.sized_order.size_dollars <= 0.01 * healthy_portfolio.nav


class TestRiskEngineDrawdownGate:
    def test_drawdown_blocks_trade(self, risk_config, bull_signal, drawn_down_portfolio):
        result = validate_order(bull_signal, drawn_down_portfolio, risk_config)
        assert not result.passed
        assert any("drawdown" in f.lower() for f in result.failures)

    def test_no_drawdown_allows_trade(self, risk_config, bull_signal, healthy_portfolio):
        result = validate_order(bull_signal, healthy_portfolio, risk_config)
        assert result.passed


class TestRiskEngineDailyLoss:
    def test_large_daily_loss_blocks(self, bull_signal):
        tight_config = RiskConfig(
            min_confidence_threshold=0.55,
            kelly_fraction=0.25,
            max_position_pct=0.05,
            max_total_exposure=3.0,
            max_drawdown=0.08,
            daily_loss_limit=5000.0,
            daily_var_limit=10000.0,
            slippage_threshold=0.02,
        )
        losing = PortfolioState(
            nav=95_000.0,
            cash=95_000.0,
            peak_equity=100_000.0,
            current_drawdown=0.05,
            positions=[],
            daily_pnl=-10000.0,
            total_exposure=0.0,
        )
        result = validate_order(bull_signal, losing, tight_config)
        assert not result.passed
        assert any("loss" in f.lower() for f in result.failures)


class TestRiskEngineAllPasses:
    def test_all_checks_pass_produces_sized_order(self, risk_config, bull_signal, healthy_portfolio):
        result = validate_order(bull_signal, healthy_portfolio, risk_config)
        assert result.passed
        assert result.sized_order.asset == "AAPL"
        assert result.sized_order.direction == Direction.LONG
        assert result.sized_order.size_dollars > 0
        assert result.sized_order.order_type == "LIMIT"
        assert result.failures == []
