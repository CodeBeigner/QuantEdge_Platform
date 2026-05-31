"""Market Scanner filters — liquidity, volume, spread, catalyst."""
from __future__ import annotations

from typing import List

from services.data.base import MarketSnapshot


def filter_liquidity(snapshots: List[MarketSnapshot], min_volume_24h: float) -> List[MarketSnapshot]:
    """Remove symbols with insufficient 24h volume."""
    return [s for s in snapshots if s.volume_24h >= min_volume_24h]


def filter_spread(snapshots: List[MarketSnapshot], max_spread_pct: float) -> List[MarketSnapshot]:
    """Remove symbols with excessive spread."""
    return [s for s in snapshots if s.spread_pct <= max_spread_pct]


def filter_unusual_volume(
    snapshots: List[MarketSnapshot],
    historical_volumes: dict,
    sigma: float = 2.0,
) -> List[MarketSnapshot]:
    """Flag symbols where current volume exceeds historical mean by N sigma."""
    flagged = []
    for s in snapshots:
        hist = historical_volumes.get(s.symbol, [])
        if not hist or len(hist) < 7:
            flagged.append(s)
            continue
        mean_vol = sum(hist) / len(hist)
        if len(hist) > 1:
            variance = sum((v - mean_vol) ** 2 for v in hist) / len(hist)
            std = variance ** 0.5
        else:
            std = 0
        if std == 0 or s.volume_24h > mean_vol + sigma * std:
            flagged.append(s)
    return flagged


def filter_significant_move(
    snapshots: List[MarketSnapshot],
    min_change_pct: float = 0.0,
) -> List[MarketSnapshot]:
    """Keep only symbols with significant price moves."""
    if min_change_pct <= 0:
        return snapshots
    return [s for s in snapshots if abs(s.change_24h_pct) >= min_change_pct]
