"""Strategy and asset class enums, strategy registration dataclass."""
from __future__ import annotations

from dataclasses import dataclass, field
from enum import Enum
from typing import List, Optional


class StrategyStyle(str, Enum):
    TREND_FOLLOWING = "TREND_FOLLOWING"
    MEAN_REVERSION = "MEAN_REVERSION"
    STAT_ARB = "STAT_ARB"
    HFT = "HFT"
    MOMENTUM = "MOMENTUM"
    VOLATILITY = "VOLATILITY"
    MACRO = "MACRO"
    CORRELATION = "CORRELATION"
    REGIME = "REGIME"
    FUNDING_SENTIMENT = "FUNDING_SENTIMENT"


class AssetClass(str, Enum):
    EQUITY = "EQUITY"
    CRYPTO = "CRYPTO"
    FOREX = "FOREX"
    FUTURES = "FUTURES"


@dataclass
class StrategyRegistration:
    name: str
    style: StrategyStyle
    asset_class: AssetClass
    max_allocation_pct: float = 15.0
    survivorship_bias_corrected: bool = False
    regime_attached: bool = False
    correlated_assets: Optional[List[str]] = None
    hft_override_acknowledged: bool = False
    created_at: str = ""


@dataclass
class SignalSuppression:
    suppressed: bool
    reason: str = ""
    regime: str = ""
    policy: str = ""
