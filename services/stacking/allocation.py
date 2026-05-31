"""Portfolio stacking optimizer — maximize Sharpe, minimize correlation, respect caps."""
from __future__ import annotations

import logging
from typing import Dict, List, Optional, Tuple

import numpy as np
from scipy.optimize import minimize

_log = logging.getLogger(__name__)


def stacking_optimizer(
    returns: Dict[str, np.ndarray],
    max_per_strategy: float = 0.15,
    max_total: float = 1.0,
) -> Dict:
    """Optimize capital allocation across strategies.

    Objective: maximize portfolio Sharpe ratio.
    Constraints: sum(weights) <= max_total, 0 <= weight_i <= max_per_strategy.
    Returns suggested weights as recommendations only — never auto-apply.
    """
    names = sorted(returns.keys())
    if len(names) < 2:
        return {
            "weights": {names[0]: 1.0} if names else {},
            "status": "insufficient_strategies",
            "message": "Need at least 2 strategies for optimization",
        }

    min_len = min(len(r) for r in returns.values())
    if min_len < 30:
        return {
            "weights": {n: 1.0 / len(names) for n in names},
            "status": "insufficient_data",
            "message": f"Need at least 30 data points per strategy, got {min_len}",
        }

    ret_matrix = np.column_stack([returns[n][:min_len] for n in names])
    mean_ret = np.mean(ret_matrix, axis=0)
    cov_matrix = np.cov(ret_matrix, rowvar=False)
    n = len(names)
    risk_free = 0.0

    def neg_sharpe(weights):
        w = np.array(weights)
        port_return = np.dot(w, mean_ret)
        port_vol = np.sqrt(np.dot(w.T, np.dot(cov_matrix, w)))
        if port_vol < 1e-10:
            return 0.0
        return -float((port_return - risk_free / 252) / port_vol)

    init_weights = np.ones(n) / n
    bounds = [(0.0, max_per_strategy)] * n
    constraints = [{"type": "ineq", "fun": lambda w: max_total - np.sum(w)}]

    result = minimize(neg_sharpe, init_weights, method="SLSQP", bounds=bounds, constraints=constraints, options={"maxiter": 1000, "ftol": 1e-12})

    weights = np.maximum(result.x, 0.0)
    weights = np.minimum(weights, max_per_strategy)
    weight_sum = weights.sum()
    if weight_sum > max_total:
        weights = weights / weight_sum * max_total
        weights = np.minimum(weights, max_per_strategy)

    weight_dict = {names[i]: round(float(weights[i]), 4) for i in range(n)}

    port_return = float(np.dot(weights, mean_ret))
    port_vol = float(np.sqrt(np.dot(weights.T, np.dot(cov_matrix, weights))))
    port_sharpe = float((port_return - risk_free / 252) / port_vol) if port_vol > 1e-10 else 0.0

    return {
        "weights": weight_dict,
        "status": "optimized",
        "portfolio_sharpe": round(port_sharpe, 4),
        "portfolio_return": round(port_return, 4),
        "portfolio_volatility": round(port_vol, 4),
        "must_confirm": True,
        "message": "Weight adjustments are recommendations only — confirm before applying",
    }


def compute_portfolio_sharpe(
    returns: Dict[str, np.ndarray],
    weights: Dict[str, float],
) -> float:
    """Compute portfolio Sharpe from strategy returns and weights."""
    if not returns or not weights:
        return 0.0
    names = sorted(returns.keys())
    min_len = min(len(returns[n]) for n in names)
    if min_len < 2:
        return 0.0

    ret_matrix = np.column_stack([returns[n][:min_len] for n in names])
    w = np.array([weights.get(n, 0.0) for n in names])
    if w.sum() == 0:
        return 0.0
    w = w / w.sum()

    port_returns = np.dot(ret_matrix, w)
    excess = port_returns.mean() - 0.0
    vol = port_returns.std(ddof=1)
    if vol < 1e-10:
        return 0.0
    return float(excess / vol * np.sqrt(252))
