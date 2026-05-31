"""Tests for historical Value at Risk computation."""
import numpy as np
import pytest
from services.risk.var import historical_var, portfolio_var


class TestHistoricalVaR:
    def test_normal_returns_95_var(self):
        np.random.seed(42)
        returns = np.random.normal(0.001, 0.02, 1000)
        var = historical_var(returns, confidence=0.95)
        assert var < 0
        assert var > -0.05

    def test_empty_returns(self):
        var = historical_var(np.array([]), confidence=0.95)
        assert var == 0.0

    def test_constant_returns(self):
        returns = np.array([0.01] * 100)
        var = historical_var(returns, confidence=0.95)
        assert var == pytest.approx(0.01)

    def test_99_confidence_more_extreme_than_95(self):
        np.random.seed(42)
        returns = np.random.normal(0.0, 0.02, 1000)
        var_95 = historical_var(returns, confidence=0.95)
        var_99 = historical_var(returns, confidence=0.99)
        assert var_99 < var_95


class TestPortfolioVaR:
    def test_portfolio_with_positions(self):
        np.random.seed(42)
        returns_history = {
            "AAPL": np.random.normal(0.001, 0.02, 500),
            "MSFT": np.random.normal(0.0008, 0.018, 500),
        }
        positions = {"AAPL": 50_000.0, "MSFT": 30_000.0}
        result = portfolio_var(returns_history, positions, confidence=0.95)
        assert isinstance(result, float)
        assert result < 0
        assert result > -5000.0

    def test_empty_portfolio(self):
        result = portfolio_var({}, {}, confidence=0.95)
        assert result == 0.0
