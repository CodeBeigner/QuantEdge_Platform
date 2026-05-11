"""Primary-signal generators used to bootstrap meta-labeler training.

These are deliberately simple rules-based strategies whose raw output the
meta-labeler learns to filter. We do NOT claim these strategies make money
standalone — their job is to produce a stream of historical signals that
the meta-labeler can score. Production trading uses the Java side's full
strategy suite; this module exists so we can train the meta-labeler without
coupling to the Java rules.
"""
from __future__ import annotations

import pandas as pd


def replay_momentum_primary(bars: pd.DataFrame, fast: int = 10, slow: int = 50) -> pd.DataFrame:
    """Emit +1 on fast-SMA-crosses-above-slow-SMA, -1 on the inverse cross.

    Returns a DataFrame with columns [time, direction]. One row per cross event.
    """
    if len(bars) <= slow:
        raise ValueError(f"not enough bars ({len(bars)}) for slow={slow}")

    closes = bars["close"].astype(float)
    fast_ma = closes.rolling(fast).mean()
    slow_ma = closes.rolling(slow).mean()

    diff = fast_ma - slow_ma
    prev = diff.shift(1)

    # Cross up: prev was <= 0 OR was NaN (not yet bullish), and now diff > 0
    # Cross dn: prev was >= 0 OR was NaN (not yet bearish), and now diff < 0
    # We treat NaN as "neutral" - it can transition to either direction
    cross_up = ((prev <= 0) | prev.isna()) & (diff > 0)
    cross_dn = ((prev >= 0) | prev.isna()) & (diff < 0)

    long_mask = cross_up & fast_ma.notna() & slow_ma.notna()
    short_mask = cross_dn & fast_ma.notna() & slow_ma.notna()

    longs = pd.DataFrame({
        "time": bars.loc[long_mask, "time"].values,
        "direction": 1,
    })
    shorts = pd.DataFrame({
        "time": bars.loc[short_mask, "time"].values,
        "direction": -1,
    })

    out = pd.concat([longs, shorts], ignore_index=True).sort_values("time").reset_index(drop=True)
    return out[["time", "direction"]]
