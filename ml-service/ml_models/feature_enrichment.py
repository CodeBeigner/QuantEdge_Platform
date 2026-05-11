"""Merge derivatives data (funding, OI) into OHLCV bars for ML features.

All merges are strictly past-or-current: at bar time t, we only pull funding/OI
rows whose time <= t. Forward-fill is used to propagate the most recent funding
rate across bars until the next funding event.
"""
from __future__ import annotations

from typing import Optional

import pandas as pd


def enrich_with_derivatives(
    bars: pd.DataFrame,
    funding: Optional[pd.DataFrame] = None,
    oi: Optional[pd.DataFrame] = None,
) -> pd.DataFrame:
    out = bars.copy().sort_values("time").reset_index(drop=True)

    # Funding rate — forward-fill the most recent funding event
    if funding is not None and not funding.empty:
        f = funding.sort_values("time").reset_index(drop=True)
        merged = pd.merge_asof(
            out[["time"]], f[["time", "funding_rate"]],
            on="time", direction="backward",
        )
        out["funding_rate"] = merged["funding_rate"].fillna(0.0).astype(float)
    else:
        out["funding_rate"] = 0.0

    # Funding delta (current vs previous)
    out["funding_rate_delta"] = out["funding_rate"].diff().fillna(0.0)

    # Open interest — merge_asof, then compute deltas
    if oi is not None and not oi.empty:
        o = oi.sort_values("time").reset_index(drop=True)
        merged = pd.merge_asof(
            out[["time"]], o[["time", "open_interest"]],
            on="time", direction="backward",
        )
        oi_series = merged["open_interest"].ffill().fillna(0.0).astype(float)
        out["open_interest"] = oi_series
        out["oi_delta_1"] = oi_series.diff(1).fillna(0.0)
        out["oi_delta_4"] = oi_series.diff(4).fillna(0.0)
    else:
        out["open_interest"] = 0.0
        out["oi_delta_1"] = 0.0
        out["oi_delta_4"] = 0.0

    return out


ENRICHED_COLS = [
    "funding_rate", "funding_rate_delta",
    "open_interest", "oi_delta_1", "oi_delta_4",
]
