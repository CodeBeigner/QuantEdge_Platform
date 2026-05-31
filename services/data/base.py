"""Abstract base class for market data providers."""
from __future__ import annotations

from abc import ABC, abstractmethod
from dataclasses import dataclass, field
from datetime import datetime
from typing import List, Optional

import pandas as pd


@dataclass
class AssetInfo:
    symbol: str
    name: str = ""
    asset_type: str = "EQUITY"  # EQUITY, CRYPTO, FOREX
    exchange: str = ""
    currency: str = "USD"


@dataclass
class MarketSnapshot:
    symbol: str
    price: float
    volume_24h: float = 0.0
    change_24h_pct: float = 0.0
    high_24h: float = 0.0
    low_24h: float = 0.0
    spread_pct: float = 0.0
    timestamp: str = field(default_factory=lambda: datetime.utcnow().isoformat())


class MarketDataProvider(ABC):
    @abstractmethod
    def fetch_ohlcv(self, symbol: str, interval: str = "1h", days: int = 30) -> pd.DataFrame:
        """Fetch OHLCV data as DataFrame with columns [open, high, low, close, volume]."""
        ...

    @abstractmethod
    def fetch_snapshot(self, symbol: str) -> Optional[MarketSnapshot]:
        """Fetch current market snapshot for a single symbol."""
        ...

    @abstractmethod
    def fetch_snapshots(self, symbols: List[str]) -> List[MarketSnapshot]:
        """Fetch snapshots for multiple symbols."""
        ...

    @abstractmethod
    def get_name(self) -> str:
        """Return provider name (e.g. 'yfinance', 'binance')."""
        ...
