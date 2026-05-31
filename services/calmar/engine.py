"""Calmar Ratio Engine — computes and benchmarks Calmar at strategy/asset/portfolio levels.

Calmar = Annualized Return / |Max Drawdown|
Where annualized_return = (final_value / initial_value) ** (252 / n_days) - 1
"""
from __future__ import annotations

import logging
from typing import Dict, List, Optional, Tuple

import numpy as np
import pandas as pd

_log = logging.getLogger(__name__)


def annualized_return(equity_curve: np.ndarray, trading_days: int = 252) -> float:
    """Compute annualized return from equity curve."""
    if len(equity_curve) < 2 or equity_curve[0] <= 0:
        return 0.0
    total_return = equity_curve[-1] / equity_curve[0] - 1
    n = len(equity_curve)
    if n <= 1:
        return 0.0
    return float((1 + total_return) ** (trading_days / n) - 1)


def max_drawdown(equity_curve: np.ndarray) -> float:
    """Compute maximum drawdown as a negative fraction."""
    if len(equity_curve) < 2:
        return 0.0
    running_max = np.maximum.accumulate(equity_curve)
    drawdowns = (equity_curve - running_max) / running_max
    return float(np.min(drawdowns))  # negative value


def compute_calmar(equity_curve: np.ndarray) -> float:
    """Compute Calmar ratio from equity curve. Returns 0 if max_drawdown is ~0."""
    ann_ret = annualized_return(equity_curve)
    dd = max_drawdown(equity_curve)
    if abs(dd) < 1e-10:
        return 0.0
    return float(ann_ret / abs(dd))


def rolling_calmar(equity_curve: np.ndarray, window_days: int = 252) -> pd.Series:
    """Compute rolling Calmar ratio over a moving window."""
    if len(equity_curve) < window_days:
        return pd.Series([], dtype=float)
    series = pd.Series(equity_curve)
    rolling_max = series.rolling(window=window_days, min_periods=window_days).max()
    rolling_dd = (series - rolling_max) / rolling_max
    rolling_final = series.shift(-window_days + 1)
    rolling_total_return = (rolling_final / series) - 1
    n_years = window_days / 252
    rolling_ann_return = (1 + rolling_total_return) ** (1 / max(n_years, 1e-10)) - 1
    rolling_dd_min = rolling_dd.rolling(window=window_days, min_periods=window_days).min()
    calmar = rolling_ann_return / abs(rolling_dd_min)
    return calmar.dropna()


def calmar_benchmark(calmar_value: float) -> Dict[str, str]:
    """Classify Calmar ratio into performance tier."""
    if calmar_value < 2.0:
        return {"tier": "underperforming", "label": "Underperforming — review or retire", "eligible_for_leverage": "no"}
    elif calmar_value < 3.0:
        return {"tier": "acceptable", "label": "Acceptable", "eligible_for_leverage": "no"}
    elif calmar_value < 5.0:
        return {"tier": "good", "label": "Good — eligible for capital increase", "eligible_for_leverage": "maybe"}
    else:
        return {"tier": "elite", "label": "Elite — flag for leverage / prop firm deployment", "eligible_for_leverage": "yes"}


def compute_strategy_calmar(trades: List[dict]) -> Dict:
    """Compute Calmar for a list of trades (each dict has 'pnl' or 'equity' field)."""
    if not trades:
        return {"calmar": 0.0, "annualized_return": 0.0, "max_drawdown": 0.0, "n_trades": 0, "benchmark": calmar_benchmark(0.0)}

    equity = [1.0]
    for t in trades:
        ret = t.get("return_pct", 0) / 100.0
        equity.append(equity[-1] * (1 + ret))

    curve = np.array(equity)
    c = compute_calmar(curve)
    ann_r = annualized_return(curve)
    dd = max_drawdown(curve)

    return {
        "calmar": round(c, 4),
        "annualized_return": round(ann_r, 4),
        "max_drawdown": round(dd, 4),
        "n_trades": len(trades),
        "benchmark": calmar_benchmark(c),
    }


def compute_portfolio_calmar(strategies: Dict[str, List[dict]], weights: Optional[Dict[str, float]] = None) -> Dict:
    """Compute portfolio-level Calmar from multiple strategy trade logs."""
    if not strategies:
        return {"calmar": 0.0, "annualized_return": 0.0, "max_drawdown": 0.0, "n_strategies": 0, "benchmark": calmar_benchmark(0.0)}

    if weights is None:
        weights = {name: 1.0 / len(strategies) for name in strategies}

    all_trades = []
    for name, trades in strategies.items():
        w = weights.get(name, 0.0)
        for t in trades:
            all_trades.append({"date": t.get("date", ""), "weighted_return": t.get("return_pct", 0) / 100.0 * w})

    if not all_trades:
        return {"calmar": 0.0, "annualized_return": 0.0, "max_drawdown": 0.0, "benchmark": calmar_benchmark(0.0)}

    all_trades.sort(key=lambda x: x["date"])
    equity = [1.0]
    for t in all_trades:
        equity.append(equity[-1] * (1 + t["weighted_return"]))

    curve = np.array(equity)
    c = compute_calmar(curve)
    ann_r = annualized_return(curve)
    dd = max_drawdown(curve)

    return {
        "calmar": round(c, 4),
        "annualized_return": round(ann_r, 4),
        "max_drawdown": round(dd, 4),
        "n_strategies": len(strategies),
        "benchmark": calmar_benchmark(c),
        "per_strategy": {
            name: compute_strategy_calmar(trades)
            for name, trades in strategies.items()
        },
    }
