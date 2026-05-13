"""Purged K-Fold cross-validation for time series with overlapping events.

Reference: Lopez de Prado, Advances in Financial Machine Learning, Ch. 7.

Each sample is an event spanning [event_start_times[i], event_end_times[i]].
A train/test split is valid only if:
  1. Train samples whose event window overlaps the test window are PURGED.
  2. Train samples immediately after the test window are EMBARGOED (removed)
     to account for autocorrelation spillover.

Design note: Start times are passed explicitly rather than inferred. Real
triple-barrier labels have variable horizons per signal (TP hit at bar 3,
SL at bar 17, TIMEOUT at max_bars), so any inference heuristic is wrong
by construction. Callers naturally have both pieces — signal_time +
outcome_time from apply_triple_barrier output.
"""
from __future__ import annotations

from typing import Iterator, Tuple

import numpy as np
import pandas as pd


class PurgedKFold:
    def __init__(
        self,
        n_splits: int,
        event_start_times: pd.Series,
        event_end_times: pd.Series,
        embargo_bars: int = 0,
    ):
        if n_splits < 2:
            raise ValueError("n_splits must be >= 2")
        if len(event_start_times) != len(event_end_times):
            raise ValueError(
                f"event_start_times length {len(event_start_times)} != "
                f"event_end_times length {len(event_end_times)}"
            )
        self.n_splits = n_splits
        self.event_start_times = event_start_times.reset_index(drop=True)
        self.event_end_times = event_end_times.reset_index(drop=True)
        self.embargo_bars = embargo_bars

    def split(self, X) -> Iterator[Tuple[np.ndarray, np.ndarray]]:
        n = len(X) if hasattr(X, "__len__") else X.shape[0]
        if n != len(self.event_end_times):
            raise ValueError(
                f"X length {n} != event_end_times length {len(self.event_end_times)}"
            )

        indices = np.arange(n)
        fold_size = n // self.n_splits
        fold_bounds = [
            (i * fold_size, (i + 1) * fold_size if i < self.n_splits - 1 else n)
            for i in range(self.n_splits)
        ]

        for start, end in fold_bounds:
            test_idx = indices[start:end]
            test_max_idx = test_idx[-1]
            test_window_start = self.event_start_times.iloc[test_idx[0]]
            test_window_end = self.event_end_times.iloc[test_idx[-1]]

            train_mask = np.ones(n, dtype=bool)
            train_mask[test_idx] = False

            # Purge: drop any train sample whose event window overlaps [test_start, test_end]
            # Two intervals [a, b] and [c, d] overlap iff a <= d AND b >= c.
            for i in np.where(train_mask)[0]:
                train_start = self.event_start_times.iloc[i]
                train_end = self.event_end_times.iloc[i]
                if train_start <= test_window_end and train_end >= test_window_start:
                    train_mask[i] = False

            # Embargo: drop train indices immediately after the test fold (positional)
            if self.embargo_bars > 0:
                embargo_end = min(n, test_max_idx + 1 + self.embargo_bars)
                train_mask[test_max_idx + 1:embargo_end] = False

            train_idx = indices[train_mask]
            yield train_idx, test_idx
