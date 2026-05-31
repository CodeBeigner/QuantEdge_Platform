"""Risk engine — 7 deterministic checks. All must pass for order to proceed."""
from __future__ import annotations

from typing import List

from services.risk.config import (
    Direction,
    PortfolioState,
    PredictionSignal,
    RiskConfig,
    RiskResult,
    SizedOrder,
)
from services.risk.kelly import apply_kelly_fraction
from services.risk.var import historical_var


def validate_order(
    signal: PredictionSignal,
    portfolio: PortfolioState,
    config: RiskConfig,
    daily_returns: list = None,
) -> RiskResult:
    failures: List[str] = []

    if signal.confidence < config.min_confidence_threshold:
        failures.append(
            f"edge_check: confidence {signal.confidence:.2%} below "
            f"threshold {config.min_confidence_threshold:.0%}"
        )

    if signal.direction == Direction.HOLD or signal.probability <= 0.5:
        failures.append(
            f"kelly_sizing: no edge (p={signal.probability:.2%})"
        )
        position_size = 0.0
    else:
        win_loss_ratio = 1.0
        if signal.take_profit and signal.stop_loss and signal.entry_price:
            reward = abs(signal.take_profit - signal.entry_price)
            risk = abs(signal.entry_price - signal.stop_loss)
            if risk > 0:
                win_loss_ratio = reward / risk
        position_size = apply_kelly_fraction(
            signal.probability,
            win_loss_ratio,
            portfolio.nav,
            config.kelly_fraction,
        )
        if position_size <= 0:
            failures.append("kelly_sizing: computed zero or negative size")

    # 3. Position limit: single position <= max_pct of NAV
    max_single = config.max_position_pct * portfolio.nav
    if position_size > max_single:
        failures.append(
            f"position_limit: ${position_size:,.0f} exceeds "
            f"max ${max_single:,.0f} ({config.max_position_pct:.0%} NAV)"
        )

    # 4. Exposure check: new + existing <= max total exposure
    new_exposure = position_size
    total_exposure_after = portfolio.total_exposure + new_exposure
    max_exposure = config.max_total_exposure * portfolio.nav
    if total_exposure_after > max_exposure:
        failures.append(
            f"exposure_check: total exposure ${total_exposure_after:,.0f} "
            f"exceeds max ${max_exposure:,.0f}"
        )

    if daily_returns and len(daily_returns) >= 30:
        var_95 = historical_var(daily_returns, confidence=0.95)
        var_dollars = abs(var_95) * position_size if var_95 < 0 else 0
        if var_dollars > config.daily_var_limit:
            failures.append(
                f"var_check: daily VaR ${var_dollars:,.0f} exceeds "
                f"limit ${config.daily_var_limit:,.0f}"
            )

    if portfolio.current_drawdown > config.max_drawdown:
        failures.append(
            f"drawdown_gate: current drawdown {portfolio.current_drawdown:.1%} "
            f"exceeds max {config.max_drawdown:.1%}"
        )

    if portfolio.daily_pnl < -config.daily_loss_limit:
        failures.append(
            f"daily_loss_limit: daily P&L ${portfolio.daily_pnl:,.0f} "
            f"exceeds limit -${config.daily_loss_limit:,.0f}"
        )

    if failures:
        return RiskResult(passed=False, sized_order=None, failures=failures)

    sized_order = SizedOrder(
        asset=signal.asset,
        direction=signal.direction,
        size_dollars=position_size,
        entry_price=signal.entry_price,
        order_type="LIMIT",
        stop_loss=signal.stop_loss,
        take_profit=signal.take_profit,
    )

    return RiskResult(
        passed=True,
        sized_order=sized_order,
        failures=[],
        applied_size=position_size,
        applied_fraction=config.kelly_fraction,
    )
