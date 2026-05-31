"""Regime-conditioned Monte Carlo simulation using HMM transition probabilities."""
from __future__ import annotations

import logging
from typing import Dict, List, Optional, Tuple

import numpy as np
from hmmlearn.hmm import GaussianHMM

_log = logging.getLogger(__name__)


def run_simulations(
    model: GaussianHMM,
    returns: np.ndarray,
    regimes: List[str],
    n_simulations: int = 10000,
    initial_capital: float = 1.0,
) -> dict:
    n_states = model.n_components
    n_days = len(returns)

    transmat = model.transmat_
    means = model.means_.flatten()
    covars = model.covars_.flatten()
    stds = np.sqrt(np.maximum(covars, 1e-10))

    regime_index = _build_regime_index(n_states)
    historical_by_regime = _build_historical_cache(returns, regimes, regime_index, n_states)

    final_returns = np.zeros(n_simulations)
    max_drawdowns = np.zeros(n_simulations)
    ruined = np.zeros(n_simulations, dtype=bool)

    rng = np.random.RandomState(42)

    for sim in range(n_simulations):
        current_state = rng.choice(n_states)

        equity = np.ones(n_days + 1)
        peak = 1.0

        for day in range(n_days):
            if len(historical_by_regime[current_state]) > 0:
                ret = rng.choice(historical_by_regime[current_state])
            else:
                ret = rng.normal(means[current_state], stds[current_state])

            equity[day + 1] = equity[day] * (1 + ret)
            peak = max(peak, equity[day + 1])

            if rng.random() > 0.5:
                current_state = rng.choice(n_states, p=transmat[current_state])

        final_returns[sim] = equity[-1] - 1.0
        max_drawdowns[sim] = 1.0 - equity.min() / peak
        if equity.min() < initial_capital * 0.5:
            ruined[sim] = True

    sorted_ret = np.sort(final_returns)
    var_95 = float(np.percentile(sorted_ret, 5))
    cvar_mask = sorted_ret <= var_95
    cvar_95 = float(sorted_ret[cvar_mask].mean()) if cvar_mask.any() else var_95

    dd_sorted = np.sort(max_drawdowns)
    return {
        "median_return": round(float(np.median(final_returns)), 6),
        "var_95": round(var_95, 6),
        "cvar_95": round(cvar_95, 6),
        "max_drawdown_distribution": {
            "p10": round(float(np.percentile(dd_sorted, 10)), 4),
            "p50": round(float(np.percentile(dd_sorted, 50)), 4),
            "p90": round(float(np.percentile(dd_sorted, 90)), 4),
        },
        "ruin_probability": round(float(ruined.mean()), 4),
    }


def _build_regime_index(n_states: int) -> Dict[str, int]:
    names = ["bear", "sideways", "bull"] if n_states >= 3 else ["bear", "bull"]
    return {names[i]: i for i in range(min(n_states, len(names)))}


def _build_historical_cache(
    returns: np.ndarray,
    regimes: List[str],
    regime_index: Dict[str, int],
    n_states: int,
) -> Dict[int, np.ndarray]:
    cache: Dict[int, list] = {i: [] for i in range(n_states)}
    for r, regime in zip(returns, regimes):
        idx = regime_index.get(regime)
        if idx is not None:
            cache[idx].append(r)
    return {k: np.array(v) if v else np.array([0.0]) for k, v in cache.items()}


def check_overfitting(live_sharpe: float, backtest_sharpe: Optional[float]) -> bool:
    if backtest_sharpe is None or backtest_sharpe <= 0:
        return False
    if live_sharpe <= 0:
        return True
    return live_sharpe < 0.5 * backtest_sharpe


def compute_sharpe(returns: np.ndarray, risk_free: float = 0.0) -> float:
    if len(returns) < 2:
        return 0.0
    excess = returns - risk_free / 252
    mean_excess = np.mean(excess)
    std_excess = np.std(excess, ddof=1)
    if std_excess < 1e-12:
        return 0.0
    return float(mean_excess / std_excess * np.sqrt(252))
