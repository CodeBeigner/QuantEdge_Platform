"""Calmar diagnostic simulations — non-destructive "what-if" analysis."""
from __future__ import annotations

import logging
from typing import Dict, List, Optional, Tuple

import numpy as np

_log = logging.getLogger(__name__)


def trailing_stop_simulation(
    trades: List[dict],
    atr_multiplier: float = 1.5,
    atr_window: int = 14,
) -> Dict:
    """Replay trade log with ATR-based trailing stop. Projected Calmar delta.

    For each trade, simulates a trailing stop that exits when price moves
    against the position by `atr_multiplier * ATR`. Reports projected Calmar
    delta vs original.
    """
    if len(trades) < atr_window + 1:
        return {"status": "insufficient_data", "message": f"Need at least {atr_window + 1} trades"}

    original = _compute_calmar_from_trades(trades)

    modified_equity = [1.0]
    for i, t in enumerate(trades):
        ret = t.get("return_pct", 0) / 100.0

        if i >= atr_window:
            recent = [
                abs(trades[j].get("return_pct", 0) / 100.0)
                for j in range(i - atr_window, i)
            ]
            atr = sum(recent) / len(recent) if recent else 0.01
            stop_distance = atr_multiplier * atr

            if ret < -stop_distance:
                ret = -stop_distance

        modified_equity.append(modified_equity[-1] * (1 + ret))

    modified = _compute_calmar_from_curve(np.array(modified_equity))
    delta = round(modified["calmar"] - original["calmar"], 4)

    return {
        "type": "trailing_stop_simulation",
        "atr_multiplier": atr_multiplier,
        "atr_window": atr_window,
        "original_calmar": original["calmar"],
        "projected_calmar": modified["calmar"],
        "calmar_delta": delta,
        "recommendation": _delta_recommendation(delta, "trailing stop"),
    }


def regime_filter_simulation(
    trades: List[dict],
    regimes: List[str],
    adverse_regimes: Optional[List[str]] = None,
) -> Dict:
    """Replay trade log with signals suppressed during adverse regimes.

    Adverse regimes default to ["bear", "high_volatility"].
    Reports projected Calmar delta vs original.
    """
    if len(trades) != len(regimes):
        return {"status": "mismatch", "message": "trades and regimes must have same length"}

    if adverse_regimes is None:
        adverse_regimes = ["bear", "high_volatility"]

    original = _compute_calmar_from_trades(trades)

    filtered_equity = [1.0]
    for i, t in enumerate(trades):
        if regimes[i] in adverse_regimes:
            filtered_equity.append(filtered_equity[-1])
        else:
            ret = t.get("return_pct", 0) / 100.0
            filtered_equity.append(filtered_equity[-1] * (1 + ret))

    modified = _compute_calmar_from_curve(np.array(filtered_equity))
    delta = round(modified["calmar"] - original["calmar"], 4)

    suppressed_count = sum(1 for r in regimes if r in adverse_regimes)

    return {
        "type": "regime_filter_simulation",
        "adverse_regimes": adverse_regimes,
        "signals_suppressed": suppressed_count,
        "suppression_rate": round(suppressed_count / len(trades), 4) if trades else 0,
        "original_calmar": original["calmar"],
        "projected_calmar": modified["calmar"],
        "calmar_delta": delta,
        "recommendation": _delta_recommendation(delta, "regime filter"),
    }


def _compute_calmar_from_trades(trades: List[dict]) -> Dict:
    equity = [1.0]
    for t in trades:
        ret = t.get("return_pct", 0) / 100.0
        equity.append(equity[-1] * (1 + ret))
    return _compute_calmar_from_curve(np.array(equity))


def _compute_calmar_from_curve(curve: np.ndarray) -> Dict:
    from services.calmar.engine import compute_calmar, annualized_return, max_drawdown
    return {
        "calmar": round(compute_calmar(curve), 4),
        "annualized_return": round(annualized_return(curve), 4),
        "max_drawdown": round(max_drawdown(curve), 4),
    }


def _delta_recommendation(delta: float, method: str) -> str:
    if delta > 0.5:
        return f"Strong improvement: {method} would significantly boost Calmar (+{delta:.2f})"
    elif delta > 0.1:
        return f"Moderate improvement: {method} shows potential (+{delta:.2f})"
    elif delta > -0.1:
        return f"Minimal impact: {method} has little effect ({delta:+.2f})"
    else:
        return f"Negative impact: {method} would reduce Calmar ({delta:+.2f}) — not recommended"
