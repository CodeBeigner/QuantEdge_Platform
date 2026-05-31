"""Slippage guard — abort if market price deviates too far from signal price."""
from __future__ import annotations

from dataclasses import dataclass


@dataclass
class SlippageResult:
    ok: bool
    deviation: float
    signal_price: float
    current_price: float
    message: str = ""


def check_slippage(
    signal_price: float,
    current_price: float,
    threshold: float = 0.02,
) -> SlippageResult:
    if signal_price <= 0:
        return SlippageResult(
            ok=False, deviation=1.0,
            signal_price=signal_price, current_price=current_price,
            message="Invalid signal price",
        )
    deviation = abs(current_price - signal_price) / signal_price
    if deviation > threshold:
        return SlippageResult(
            ok=False, deviation=round(deviation, 4),
            signal_price=signal_price, current_price=current_price,
            message=(
                f"Slippage {deviation:.2%} exceeds {threshold:.2%} threshold. "
                f"Signal: ${signal_price:.2f}, Market: ${current_price:.2f}"
            ),
        )
    return SlippageResult(
        ok=True, deviation=round(deviation, 4),
        signal_price=signal_price, current_price=current_price,
        message="",
    )
