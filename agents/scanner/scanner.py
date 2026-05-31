"""Market Scanner — scans watchlists and produces ranked OpportunityLists."""
from __future__ import annotations

import logging
from dataclasses import dataclass, field
from datetime import datetime
from typing import Dict, List, Optional

from services.data.base import MarketDataProvider, MarketSnapshot
from agents.scanner.config import ScannerConfig
from agents.scanner import filters

_log = logging.getLogger(__name__)


@dataclass
class Opportunity:
    asset: str
    asset_type: str
    price: float
    change_24h_pct: float
    volume_24h: float
    signal_strength: float          # 0.0–1.0 composite score
    reasons: List[str] = field(default_factory=list)
    snapshot: Optional[MarketSnapshot] = None
    timestamp: str = field(default_factory=lambda: datetime.utcnow().isoformat())


@dataclass
class OpportunityList:
    opportunities: List[Opportunity] = field(default_factory=list)
    scanned_count: int = 0
    filtered_count: int = 0
    timestamp: str = field(default_factory=lambda: datetime.utcnow().isoformat())

    def top(self, n: int = 5) -> List[Opportunity]:
        sorted_ops = sorted(self.opportunities, key=lambda o: o.signal_strength, reverse=True)
        return sorted_ops[:n]


class MarketScanner:
    def __init__(
        self,
        provider: MarketDataProvider,
        config: Optional[ScannerConfig] = None,
    ):
        self.provider = provider
        self.config = config or ScannerConfig()
        self._volume_history: Dict[str, List[float]] = {}
        self._last_scan: Optional[OpportunityList] = None

    def scan(self) -> OpportunityList:
        if not self.config.enabled:
            return OpportunityList()

        symbols = self.config.symbols
        _log.info("Scanning %d symbols via %s", len(symbols), self.provider.get_name())

        snapshots = self.provider.fetch_snapshots(symbols)
        scanned = len(snapshots)

        snapshots = filters.filter_liquidity(snapshots, self.config.min_volume_24h)
        snapshots = filters.filter_spread(snapshots, self.config.max_spread_pct)
        snapshots = filters.filter_unusual_volume(
            snapshots, self._volume_history, self.config.unusual_volume_sigma
        )
        snapshots = filters.filter_significant_move(
            snapshots, self.config.min_change_abs_pct
        )

        opportunities = []
        for snap in snapshots:
            self._update_volume_history(snap.symbol, snap.volume_24h)

            strength = self._compute_signal_strength(snap)
            reasons = self._build_reasons(snap)

            opportunities.append(Opportunity(
                asset=snap.symbol,
                asset_type="CRYPTO" if "-" in snap.symbol else "EQUITY",
                price=snap.price,
                change_24h_pct=snap.change_24h_pct,
                volume_24h=snap.volume_24h,
                signal_strength=strength,
                reasons=reasons,
                snapshot=snap,
            ))

        result = OpportunityList(
            opportunities=sorted(opportunities, key=lambda o: o.signal_strength, reverse=True),
            scanned_count=scanned,
            filtered_count=len(opportunities),
        )

        self._last_scan = result
        _log.info("Scan complete: %d scanned, %d passed filters", scanned, len(opportunities))
        return result

    def _compute_signal_strength(self, snap: MarketSnapshot) -> float:
        """Compute composite signal strength 0.0–1.0."""
        score = 0.5

        change_score = min(abs(snap.change_24h_pct) / 10.0, 0.3)
        score += change_score * (1 if snap.change_24h_pct > 0 else 0.8)

        mean_vol = sum(self._volume_history.get(snap.symbol, [1])) / max(len(self._volume_history.get(snap.symbol, [1])), 1)
        if mean_vol > 0:
            vol_ratio = snap.volume_24h / mean_vol
            vol_score = min((vol_ratio - 1) / 3, 0.2)
            score += vol_score

        return round(min(max(score, 0.0), 1.0), 4)

    def _build_reasons(self, snap: MarketSnapshot) -> List[str]:
        reasons = []
        if abs(snap.change_24h_pct) >= 3.0:
            reasons.append(f"Significant move: {snap.change_24h_pct:+.1f}%")
        mean_vol = sum(self._volume_history.get(snap.symbol, [1])) / max(len(self._volume_history.get(snap.symbol, [1])), 1)
        if mean_vol > 0 and snap.volume_24h > mean_vol * self.config.unusual_volume_sigma:
            reasons.append("Unusual volume")
        return reasons

    def _update_volume_history(self, symbol: str, volume: float):
        if symbol not in self._volume_history:
            self._volume_history[symbol] = []
        self._volume_history[symbol].append(volume)
        if len(self._volume_history[symbol]) > 30:
            self._volume_history[symbol] = self._volume_history[symbol][-30:]

    def get_last_scan(self) -> Optional[OpportunityList]:
        return self._last_scan
