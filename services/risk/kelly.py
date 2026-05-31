"""Kelly Criterion position sizing."""
from __future__ import annotations


def kelly_fraction(win_probability: float, win_loss_ratio: float) -> float:
    """
    f* = (p * b - q) / b
    where p = win_probability, q = 1 - p, b = win_loss_ratio.
    Returns the optimal fraction of capital to allocate.
    """
    loss_probability = 1.0 - win_probability
    if win_loss_ratio == 0:
        return 0.0
    f_star = (win_probability * win_loss_ratio - loss_probability) / win_loss_ratio
    return max(0.0, f_star)


def apply_kelly_fraction(
    p: float,
    b: float,
    nav: float,
    fraction: float = 0.25,
) -> float:
    """Returns position size in dollars using fractional Kelly."""
    if nav <= 0:
        return 0.0
    f_star = kelly_fraction(p, b)
    return f_star * fraction * nav
