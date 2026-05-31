"""Portfolio equity curve — combined + individual strategy curves."""
from __future__ import annotations

from typing import Dict, List

import numpy as np


def combined_equity_curve(
    strategy_returns: Dict[str, List[float]],
    weights: Dict[str, float],
    initial_capital: float = 1.0,
) -> Dict:
    """Compute combined portfolio equity curve from strategy returns and weights.

    Returns data structure for Recharts LineChart: hero line (combined) + muted subordinate lines.
    """
    if not strategy_returns:
        return {"combined": [], "strategies": {}, "timestamps": []}

    names = sorted(strategy_returns.keys())
    max_len = max(len(r) for r in strategy_returns.values())

    curves = {}
    for name in names:
        rets = strategy_returns[name]
        curve = [initial_capital]
        for r in rets:
            curve.append(curve[-1] * (1 + r))
        curves[name] = curve + [curve[-1]] * (max_len - len(rets))

        if len(curves[name]) < len(curve):
            curves[name].extend([curves[name][-1]] * (len(curve) - len(curves[name])))

    combined = [initial_capital]
    for i in range(max_len):
        weighted_return = 0.0
        for name in names:
            w = weights.get(name, 0.0)
            if i < len(strategy_returns[name]):
                weighted_return += w * strategy_returns[name][i]
        combined.append(combined[-1] * (1 + weighted_return))

    return {
        "combined": combined,
        "strategies": {name: curves[name][:len(combined)] for name in names},
        "n_points": len(combined),
    }
