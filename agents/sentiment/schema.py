"""Sentiment Agent output schema."""
from __future__ import annotations

from dataclasses import dataclass, field
from typing import List, Optional


@dataclass
class SentimentSignal:
    symbol: str
    sentiment: str = "NEUTRAL"  # BULLISH, BEARISH, NEUTRAL
    confidence: float = 0.0
    source_count: int = 0
    key_themes: List[str] = field(default_factory=list)
    narrative_summary: str = ""
    narrative_vs_price: str = ""  # e.g., "Narrative bullish, price lagging — potential signal"
    timestamp: str = ""
