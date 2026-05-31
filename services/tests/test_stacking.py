"""Tests for Portfolio Stacking Engine."""
import numpy as np
import pytest
from services.stacking.correlation import pair_matrix, correlation_heatmap_data, detect_concentration_risk
from services.stacking.allocation import stacking_optimizer, compute_portfolio_sharpe
from services.stacking.equity_curve import combined_equity_curve


@pytest.fixture
def strategy_returns():
    rng = np.random.RandomState(42)
    return {
        "trend_following": list(rng.normal(0.002, 0.015, 200)),
        "mean_reversion": list(rng.normal(0.001, 0.010, 200)),
        "stat_arb": list(rng.normal(0.0015, 0.012, 200)),
    }


@pytest.fixture
def correlated_returns():
    rng = np.random.RandomState(42)
    base = rng.normal(0.001, 0.02, 200)
    return {
        "strat_a": list(base + rng.normal(0, 0.005, 200)),
        "strat_b": list(base + rng.normal(0, 0.005, 200)),
    }


class TestCorrelation:
    def test_pair_matrix_shape(self, strategy_returns):
        result = pair_matrix(strategy_returns)
        assert len(result["matrix"]) == 3
        assert len(result["matrix"][0]) == 3

    def test_diagonal_is_one(self, strategy_returns):
        result = pair_matrix(strategy_returns)
        for i in range(3):
            assert result["matrix"][i][i] == 1.0

    def test_correlated_pair_flagged(self, correlated_returns):
        result = pair_matrix(correlated_returns)
        assert result["flagged_count"] >= 1

    def test_single_strategy(self):
        result = pair_matrix({"only": [0.01, 0.02, -0.01]})
        assert result["n_strategies"] == 1
        assert len(result["flagged_pairs"]) == 0

    def test_heatmap_data(self, strategy_returns):
        data = correlation_heatmap_data(strategy_returns)
        assert len(data) == 9


class TestConcentrationRisk:
    def test_detect_concentration(self):
        alloc = {"strat_a": 30.0, "strat_b": 10.0}
        warnings = detect_concentration_risk(alloc)
        assert len(warnings) >= 1
        assert "strat_a" in warnings[0]["strategy"]

    def test_no_concentration(self):
        alloc = {"strat_a": 12.0, "strat_b": 8.0}
        warnings = detect_concentration_risk(alloc)
        assert len(warnings) == 0


class TestStackingOptimizer:
    def test_returns_weight_dict(self, strategy_returns):
        returns_np = {k: np.array(v) for k, v in strategy_returns.items()}
        result = stacking_optimizer(returns_np)
        assert len(result["weights"]) == 3
        total = sum(result["weights"].values())
        assert total <= 1.01
        assert total > 0

    def test_must_confirm(self, strategy_returns):
        returns_np = {k: np.array(v) for k, v in strategy_returns.items()}
        result = stacking_optimizer(returns_np)
        assert result["must_confirm"]

    def test_single_strategy(self):
        returns = {"only": np.array([0.01, 0.02, -0.01, 0.005] * 10)}
        result = stacking_optimizer(returns)
        assert result["status"] == "insufficient_strategies"

    def test_allocation_caps(self, strategy_returns):
        returns_np = {k: np.array(v) for k, v in strategy_returns.items()}
        result = stacking_optimizer(returns_np, max_per_strategy=0.15)
        for w in result["weights"].values():
            assert w <= 0.15


class TestPortfolioSharpe:
    def test_compute_sharpe(self, strategy_returns):
        returns_np = {k: np.array(v) for k, v in strategy_returns.items()}
        weights = {k: 1.0 / 3 for k in strategy_returns}
        sharpe = compute_portfolio_sharpe(returns_np, weights)
        assert sharpe > 0


class TestEquityCurve:
    def test_combined_curve(self, strategy_returns):
        weights = {k: 1.0 / 3 for k in strategy_returns}
        result = combined_equity_curve(strategy_returns, weights)
        assert "combined" in result
        assert "strategies" in result
        assert len(result["combined"]) > 0

    def test_hero_curve_longer_than_inputs(self, strategy_returns):
        weights = {k: 1.0 / 3 for k in strategy_returns}
        result = combined_equity_curve(strategy_returns, weights)
        assert len(result["combined"]) == max(len(v) for v in strategy_returns.values()) + 1
