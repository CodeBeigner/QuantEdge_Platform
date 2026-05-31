"""Per-strategy enforcement rules — signal suppression, allocation caps, registration checks."""
from __future__ import annotations

import logging
from typing import List, Optional

from services.classifier.strategy_style import StrategyStyle, AssetClass, StrategyRegistration, SignalSuppression

_log = logging.getLogger(__name__)


def enforce_policy(registration: StrategyRegistration, current_regime: str, current_correlation: Optional[float] = None) -> SignalSuppression:
    """Apply per-strategy enforcement rules. Returns whether signal should be suppressed."""
    style = registration.style
    asset = registration.asset_class

    if style == StrategyStyle.MEAN_REVERSION:
        if not registration.regime_attached:
            return SignalSuppression(
                suppressed=True,
                reason="MEAN_REVERSION requires regime classifier attachment",
                policy="regime_attachment_mandatory",
            )
        if current_regime in ["bull", "bear"]:
            return SignalSuppression(
                suppressed=True,
                reason=f"MEAN_REVERSION auto-suppressed in {current_regime} regime — trending markets degrade mean reversion signals",
                regime=current_regime,
                policy="suppress_in_trending",
            )

    if style == StrategyStyle.STAT_ARB:
        if not registration.correlated_assets or len(registration.correlated_assets) < 2:
            return SignalSuppression(
                suppressed=True,
                reason="STAT_ARB requires two correlated assets at registration",
                policy="correlated_assets_required",
            )
        if current_correlation is not None:
            if current_correlation < 0.60:
                return SignalSuppression(
                    suppressed=True,
                    reason=f"STAT_ARB suppressed — correlation {current_correlation:.2f} below 0.60 threshold",
                    policy="correlation_threshold_hard",
                )
            if current_correlation < 0.75:
                _log.warning("STAT_ARB correlation %.2f below 0.75 warning threshold", current_correlation)

    if style == StrategyStyle.HFT:
        if not registration.hft_override_acknowledged:
            return SignalSuppression(
                suppressed=True,
                reason="HFT strategy requires explicit override acknowledgment",
                policy="hft_override_required",
            )

    if style == StrategyStyle.TREND_FOLLOWING:
        if not registration.survivorship_bias_corrected and asset == AssetClass.EQUITY:
            _log.warning("TREND_FOLLOWING on EQUITY without survivorship bias correction — backtests may be upward-biased")

    if asset == AssetClass.FUTURES and current_regime == "high_volatility":
        return SignalSuppression(
            suppressed=True,
            reason=f"{asset.value} signals suppressed in high_volatility regime",
            regime=current_regime,
            policy="futures_volatility_gate",
        )

    return SignalSuppression(suppressed=False)


def validate_registration(registration: StrategyRegistration) -> List[str]:
    """Validate strategy registration. Returns list of validation warnings."""
    warnings = []
    style = registration.style

    if style == StrategyStyle.STAT_ARB and (not registration.correlated_assets or len(registration.correlated_assets) < 2):
        warnings.append("STAT_ARB requires two correlated assets at registration")

    if style == StrategyStyle.HFT and not registration.hft_override_acknowledged:
        warnings.append("HFT registration warning — live execution risks are elevated. Explicit override required.")

    if style == StrategyStyle.MEAN_REVERSION and not registration.regime_attached:
        warnings.append("MEAN_REVERSION regime classifier attachment is mandatory. No signals will fire without it.")

    if style == StrategyStyle.TREND_FOLLOWING and not registration.survivorship_bias_corrected and registration.asset_class == AssetClass.EQUITY:
        warnings.append("TREND_FOLLOWING on EQUITY without survivorship bias correction — backtests may be upward-biased. Consider Norgate Data ($27/mo) or Polygon.io ($29/mo) for bias-free data.")

    return warnings


def allocation_cap(registration: StrategyRegistration, portfolio_nav: float) -> float:
    """Compute maximum allocation for a strategy. Returns dollar amount."""
    base_cap = registration.max_allocation_pct / 100.0 * portfolio_nav

    if registration.style == StrategyStyle.MEAN_REVERSION:
        base_cap = min(base_cap, 0.40 * portfolio_nav)
    elif registration.style == StrategyStyle.STAT_ARB:
        base_cap = min(base_cap, 0.30 * portfolio_nav)
    elif registration.style == StrategyStyle.HFT:
        base_cap = min(base_cap, 0.10 * portfolio_nav)

    return round(min(base_cap, 0.15 * portfolio_nav), 2)
