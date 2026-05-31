"""Historical Value at Risk computation."""
from __future__ import annotations

from typing import Dict

import numpy as np


def historical_var(returns: np.ndarray, confidence: float = 0.95) -> float:
    """Compute historical VaR at the given confidence level."""
    if len(returns) == 0:
        return 0.0
    alpha = 1.0 - confidence
    return float(np.percentile(returns, alpha * 100))


def portfolio_var(
    returns_history: Dict[str, np.ndarray],
    positions: Dict[str, float],
    confidence: float = 0.95,
    n_simulations: int = 10000,
) -> float:
    """Compute portfolio VaR via bootstrapped historical simulation."""
    if not positions or not returns_history:
        return 0.0

    assets = list(positions.keys())
    available = [a for a in assets if a in returns_history]
    if not available:
        return 0.0

    min_len = min(len(returns_history[a]) for a in available)
    if min_len < 2:
        return 0.0

    position_values = np.array([positions[a] for a in available])
    np.random.seed(42)
    indices = np.random.randint(0, min_len, size=(n_simulations,))

    simulated_returns = []
    for a in available:
        rets = returns_history[a][-min_len:]
        simulated_returns.append(rets[indices])

    simulated_returns = np.array(simulated_returns)
    portfolio_returns = (simulated_returns * position_values[:, None]).sum(axis=0)
    portfolio_returns = portfolio_returns / position_values.sum()

    return historical_var(portfolio_returns, confidence)
