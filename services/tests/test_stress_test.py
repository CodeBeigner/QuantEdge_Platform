"""Tests for stress-test module: HMM, Monte Carlo, overfitting guard."""
import numpy as np
import pytest

from services.stress_test.hmm_layer import fit_hmm, get_transition_matrix, regime_performance
from services.stress_test.monte_carlo import (
    run_simulations,
    check_overfitting,
    compute_sharpe,
    _build_regime_index,
    _build_historical_cache,
)


def generate_synthetic_returns(n: int = 200, seed: int = 42) -> np.ndarray:
    rng = np.random.RandomState(seed)
    regimes = rng.choice(3, size=n, p=[0.3, 0.4, 0.3])
    means = {-1: -0.008, 0: 0.001, 1: 0.012}
    stds = {-1: 0.02, 0: 0.01, 1: 0.015}
    returns = np.array([rng.normal(means.get(r, 0), stds.get(r, 0.01)) for r in regimes])
    return returns


@pytest.fixture
def synthetic_returns():
    return generate_synthetic_returns(200)


class TestHMMFitting:
    def test_fit_hmm_3_states(self, synthetic_returns):
        model, probs, regimes = fit_hmm(synthetic_returns, n_states=3)
        assert model.n_components == 3
        assert len(regimes) == len(synthetic_returns)

    def test_fit_hmm_2_states(self, synthetic_returns):
        model, probs, regimes = fit_hmm(synthetic_returns, n_states=2)
        assert model.n_components == 2

    def test_fit_hmm_insufficient_data(self):
        with pytest.raises(ValueError):
            fit_hmm(np.array([0.01, 0.02]), n_states=3)

    def test_transition_matrix_shape(self, synthetic_returns):
        model, _, _ = fit_hmm(synthetic_returns, n_states=3)
        transmat = get_transition_matrix(model)
        assert len(transmat) == 3
        assert len(transmat[0]) == 3

    def test_regime_names_sorted_by_return(self, synthetic_returns):
        _, _, regimes = fit_hmm(synthetic_returns, n_states=3)
        assert "bull" in regimes or "bear" in regimes

    def test_regime_performance(self, synthetic_returns):
        _, _, regimes = fit_hmm(synthetic_returns, n_states=3)
        perf = regime_performance(synthetic_returns, regimes)
        assert len(perf) > 0
        for r, p in perf.items():
            assert "avg_return" in p
            assert "win_rate" in p
            assert 0.0 <= p["win_rate"] <= 1.0


class TestMonteCarlo:
    def test_output_shape(self, synthetic_returns):
        model, _, regimes = fit_hmm(synthetic_returns, n_states=3)
        result = run_simulations(model, synthetic_returns, regimes, n_simulations=1000)
        assert "median_return" in result
        assert "var_95" in result
        assert "cvar_95" in result
        assert "ruin_probability" in result
        assert "max_drawdown_distribution" in result
        dd = result["max_drawdown_distribution"]
        assert dd["p10"] <= dd["p50"] <= dd["p90"]

    def test_var_less_than_median(self, synthetic_returns):
        model, _, regimes = fit_hmm(synthetic_returns, n_states=3)
        result = run_simulations(model, synthetic_returns, regimes, n_simulations=2000)
        assert result["var_95"] <= result["median_return"]

    def test_cvar_more_extreme_than_var(self, synthetic_returns):
        model, _, regimes = fit_hmm(synthetic_returns, n_states=3)
        result = run_simulations(model, synthetic_returns, regimes, n_simulations=2000)
        assert result["cvar_95"] <= result["var_95"]

    def test_ruin_probability_between_0_and_1(self, synthetic_returns):
        model, _, regimes = fit_hmm(synthetic_returns, n_states=3)
        result = run_simulations(model, synthetic_returns, regimes, n_simulations=1000)
        assert 0.0 <= result["ruin_probability"] <= 1.0

    def test_deterministic_seed(self, synthetic_returns):
        model, _, regimes = fit_hmm(synthetic_returns, n_states=3)
        r1 = run_simulations(model, synthetic_returns, regimes, n_simulations=100)
        r2 = run_simulations(model, synthetic_returns, regimes, n_simulations=100)
        assert r1["median_return"] == r2["median_return"]


class TestOverfittingGuard:
    def test_no_overfitting_when_live_good(self):
        assert not check_overfitting(live_sharpe=2.0, backtest_sharpe=2.5)

    def test_overfitting_when_live_poor(self):
        assert check_overfitting(live_sharpe=0.8, backtest_sharpe=3.0)

    def test_no_overfitting_when_backtest_none(self):
        assert not check_overfitting(live_sharpe=0.5, backtest_sharpe=None)

    def test_live_barely_below_threshold(self):
        assert not check_overfitting(live_sharpe=1.5, backtest_sharpe=3.0)

    def test_live_barely_above_threshold(self):
        assert not check_overfitting(live_sharpe=1.50001, backtest_sharpe=3.0)

    def test_negative_live_sharpe(self):
        assert check_overfitting(live_sharpe=-0.5, backtest_sharpe=2.0)

    def test_negative_backtest_sharpe(self):
        assert not check_overfitting(live_sharpe=1.0, backtest_sharpe=-1.0)


class TestSharpeComputation:
    def test_positive_returns(self):
        returns = np.array([0.01] * 100)
        sharpe = compute_sharpe(returns)
        assert sharpe == 0.0

    def test_normal_returns(self):
        rng = np.random.RandomState(42)
        returns = rng.normal(0.001, 0.02, 252)
        sharpe = compute_sharpe(returns)
        assert sharpe > 0.0
        assert sharpe < 5.0

    def test_insufficient_data(self):
        assert compute_sharpe(np.array([0.01])) == 0.0


class TestRegimeCache:
    def test_build_index_3_states(self):
        idx = _build_regime_index(3)
        assert idx["bear"] == 0
        assert idx["sideways"] == 1
        assert idx["bull"] == 2

    def test_build_index_2_states(self):
        idx = _build_regime_index(2)
        assert idx["bear"] == 0
        assert idx["bull"] == 1

    def test_build_historical_cache(self, synthetic_returns):
        _, _, regimes = fit_hmm(synthetic_returns, n_states=3)
        idx = _build_regime_index(3)
        cache = _build_historical_cache(synthetic_returns, regimes, idx, 3)
        assert len(cache) == 3
        total = sum(len(v) for v in cache.values())
        assert total >= len(synthetic_returns)
