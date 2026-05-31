"""Market Scanner configuration."""
from __future__ import annotations

import os
from dataclasses import dataclass, field
from typing import List


@dataclass
class ScannerConfig:
    symbols: List[str] = field(default_factory=lambda: os.getenv("SCANNER_SYMBOLS", "AAPL,MSFT,GOOGL,AMZN,NVDA,TSLA,META,BTC-USD,ETH-USD").split(","))
    min_volume_24h: float = float(os.getenv("SCANNER_MIN_VOLUME_24H", "1000000"))
    unusual_volume_sigma: float = float(os.getenv("SCANNER_UNUSUAL_VOLUME_SIGMA", "2.0"))
    max_spread_pct: float = float(os.getenv("SCANNER_MAX_SPREAD_PCT", "1.0"))
    min_change_abs_pct: float = float(os.getenv("SCANNER_MIN_CHANGE_ABS_PCT", "0.0"))
    enabled: bool = os.getenv("SCANNER_ENABLED", "true").lower() == "true"
