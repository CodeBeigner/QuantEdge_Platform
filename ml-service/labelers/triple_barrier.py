"""Triple-barrier labeler (López de Prado, Advances in Financial ML, Ch. 3).

Given a DataFrame of forward-looking OHLCV bars and a DataFrame of primary signals,
produce binary labels: 1 if take-profit hit first, 0 if stop-loss hit first, -1 if
neither hit within max_bars.

The output can be used to train a meta-labeler that predicts P(TP hit first) given
the state at signal time — without look-ahead bias, because labels come from
simulated forward paths, not future features.

Signal DataFrame contract:
    time:      timestamp of the primary signal (must match a bar time in `bars`)
    direction: +1 for long, -1 for short

Bars DataFrame contract:
    time:      timestamp (UTC, sorted ascending, unique)
    close:     float close price
    (optional high/low — used for intra-bar barrier touches if present)

Returned columns:
    signal_time, direction, entry_price, label (in {1, 0, -1}),
    outcome ("TP" | "SL" | "TIMEOUT"), outcome_time, outcome_bar, return_pct.
"""
from __future__ import annotations

import numpy as np
import pandas as pd


def apply_triple_barrier(
    bars: pd.DataFrame,
    signals: pd.DataFrame,
    tp_pct: float,
    sl_pct: float,
    max_bars: int,
) -> pd.DataFrame:
    if tp_pct <= 0 or sl_pct <= 0:
        raise ValueError("tp_pct and sl_pct must be positive fractions")
    if max_bars < 1:
        raise ValueError("max_bars must be >= 1")

    valid_dirs = {-1, 1}
    if not set(signals["direction"].unique()).issubset(valid_dirs):
        raise ValueError("direction must be +1 (long) or -1 (short)")

    bars = bars.sort_values("time").reset_index(drop=True)
    time_to_idx = {t: i for i, t in enumerate(bars["time"])}

    has_hl = "high" in bars.columns and "low" in bars.columns
    closes = bars["close"].to_numpy()
    highs = bars["high"].to_numpy() if has_hl else closes
    lows = bars["low"].to_numpy() if has_hl else closes

    out_rows = []
    for _, sig in signals.iterrows():
        t = sig["time"]
        direction = int(sig["direction"])
        if t not in time_to_idx:
            continue
        i0 = time_to_idx[t]
        if i0 + max_bars >= len(bars):
            # Can't observe a full horizon — drop this signal
            continue

        entry = closes[i0]
        if direction == 1:
            tp_level = entry * (1 + tp_pct)
            sl_level = entry * (1 - sl_pct)
        else:
            tp_level = entry * (1 - tp_pct)
            sl_level = entry * (1 + sl_pct)

        outcome = "TIMEOUT"
        outcome_bar = max_bars
        exit_price = closes[i0 + max_bars]

        for step in range(1, max_bars + 1):
            j = i0 + step
            hi, lo = highs[j], lows[j]
            if direction == 1:
                if hi >= tp_level:
                    outcome, outcome_bar, exit_price = "TP", step, tp_level
                    break
                if lo <= sl_level:
                    outcome, outcome_bar, exit_price = "SL", step, sl_level
                    break
            else:
                if lo <= tp_level:
                    outcome, outcome_bar, exit_price = "TP", step, tp_level
                    break
                if hi >= sl_level:
                    outcome, outcome_bar, exit_price = "SL", step, sl_level
                    break

        label = {"TP": 1, "SL": 0, "TIMEOUT": -1}[outcome]
        ret = (exit_price - entry) / entry * direction

        out_rows.append({
            "signal_time": t,
            "direction": direction,
            "entry_price": float(entry),
            "label": label,
            "outcome": outcome,
            "outcome_time": bars["time"].iloc[i0 + outcome_bar],
            "outcome_bar": outcome_bar,
            "return_pct": float(ret),
        })

    cols = ["signal_time", "direction", "entry_price", "label", "outcome",
            "outcome_time", "outcome_bar", "return_pct"]
    return pd.DataFrame(out_rows, columns=cols)
