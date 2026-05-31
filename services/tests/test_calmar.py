"""Tests for Calmar Ratio Engine."""
import numpy as np
import pytest
from services.calmar.engine import (
    annualized_return,
    max_drawdown,
    compute_calmar,
    calmar_benchmark,
    rolling_calmar,
    compute_strategy_calmar,
    compute_portfolio_calmar,
)
from services.calmar.diagnostics import trailing_stop_simulation, regime_filter_simulation


@pytest.fixture
def equity_up():
    return np.array([100.0, 101.0, 103.0, 102.0, 105.0, 108.0, 110.0])


@pytest.fixture
def equity_flat():
    return np.array([100.0] * 20)


@pytest.fixture
def equity_down():
    return np.array([100.0, 98.0, 95.0, 93.0, 90.0, 88.0, 85.0])


@pytest.fixture
def sample_trades():
    trades = []
    equity = 1.0
    for i in range(50):
        ret_pct = np.random.RandomState(42).normal(0.2, 1.0)
        trades.append({"date": f"2025-{i+1:02d}-01", "return_pct": ret_pct, "pnl": ret_pct * equity * 0.01})
        equity *= (1 + ret_pct / 100.0)
    return trades


class TestAnnualizedReturn:
    def test_up_trend(self, equity_up):
        r = annualized_return(equity_up)
        assert r > 0

    def test_down_trend(self, equity_down):
        r = annualized_return(equity_down)
        assert r < 0

    def test_single_point(self):
        assert annualized_return(np.array([100.0])) == 0.0

    def test_zero_start(self):
        assert annualized_return(np.array([0.0, 1.0, 2.0])) == 0.0


class TestMaxDrawdown:
    def test_no_drawdown(self):
        dd = max_drawdown(np.array([100.0, 101.0, 103.0, 105.0, 108.0, 110.0]))
        assert dd == 0.0

    def test_with_drawdown(self, equity_down):
        dd = max_drawdown(equity_down)
        assert dd < 0

    def test_drawdown_magnitude(self):
        curve = np.array([100.0, 90.0, 95.0, 85.0])
        dd = max_drawdown(curve)
        assert dd == pytest.approx(-0.15)


class TestCalmar:
    def test_positive_calmar(self, equity_up):
        c = compute_calmar(equity_up)
        assert c >= 0

    def test_flat_calmar(self, equity_flat):
        c = compute_calmar(equity_flat)
        assert c == 0.0

    def test_negative_calmar(self, equity_down):
        c = compute_calmar(equity_down)
        assert c < 0


class TestCalmarBenchmark:
    def test_underperforming(self):
        b = calmar_benchmark(1.0)
        assert b["tier"] == "underperforming"

    def test_acceptable(self):
        b = calmar_benchmark(2.5)
        assert b["tier"] == "acceptable"

    def test_good(self):
        b = calmar_benchmark(4.0)
        assert b["tier"] == "good"

    def test_elite(self):
        b = calmar_benchmark(6.0)
        assert b["tier"] == "elite"


class TestRollingCalmar:
    def test_insufficient_data(self):
        result = rolling_calmar(np.array([100.0, 101.0, 102.0]), window_days=252)
        assert len(result) == 0


class TestStrategyCalmar:
    def test_compute_from_trades(self, sample_trades):
        result = compute_strategy_calmar(sample_trades)
        assert "calmar" in result
        assert "annualized_return" in result
        assert "max_drawdown" in result
        assert result["n_trades"] == 50

    def test_empty_trades(self):
        result = compute_strategy_calmar([])
        assert result["n_trades"] == 0


class TestPortfolioCalmar:
    def test_compute_from_multiple_strategies(self, sample_trades):
        result = compute_portfolio_calmar({"strat_a": sample_trades, "strat_b": sample_trades})
        assert "calmar" in result
        assert "per_strategy" in result
        assert len(result["per_strategy"]) == 2

    def test_empty_portfolio(self):
        result = compute_portfolio_calmar({})
        assert result["n_strategies"] == 0


class TestTrailingStopDiagnostic:
    def test_insufficient_data(self):
        result = trailing_stop_simulation([{"return_pct": 1.0}], atr_multiplier=1.5)
        assert result["status"] == "insufficient_data"

    def test_with_sufficient_data(self, sample_trades):
        result = trailing_stop_simulation(sample_trades, atr_multiplier=1.5)
        assert "original_calmar" in result
        assert "projected_calmar" in result
        assert "calmar_delta" in result


class TestRegimeFilterDiagnostic:
    def test_mismatched_lengths(self):
        result = regime_filter_simulation(
            [{"return_pct": 1.0}], regimes=["bull", "bear"]
        )
        assert result["status"] == "mismatch"

    def test_with_matching_data(self, sample_trades):
        regimes = ["bull" if i % 2 == 0 else "bear" for i in range(len(sample_trades))]
        result = regime_filter_simulation(sample_trades, regimes)
        assert "original_calmar" in result
        assert "projected_calmar" in result
        assert "signals_suppressed" in result
