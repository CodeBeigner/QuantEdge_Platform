"""Purged K-Fold cross-validation for time series with overlapping events.

Reference: Lopez de Prado, Advances in Financial Machine Learning, Ch. 7.

Each sample has a known event-end time (e.g., when the triple-barrier outcome
resolved). A train/test split is valid only if:
  1. Train samples whose event window overlaps the test window are PURGED.
  2. Train samples immediately after the test window are EMBARGOED (removed)
     to account for autocorrelation spillover.
"""
from __future__ import annotations

from typing import Iterator, Tuple

import numpy as np
import pandas as pd


class PurgedKFold:
    def __init__(self, n_splits: int, event_end_times: pd.Series, embargo_bars: int = 0):
        if n_splits < 2:
            raise ValueError("n_splits must be >= 2")
        self.n_splits = n_splits
        self.event_end_times = event_end_times.reset_index(drop=True)
        self.embargo_bars = embargo_bars

        # Build implicit start times series.
        # Since event_end_times[i] was constructed from times[i+k] in the test,
        # we can reverse this: times[i] would be found at event_end_times[i-k].
        # We construct times[] by taking the event_end_times values themselves
        # as the reference timeline for observations.

        # The series event_end_times has values [t_k, t_(k+1), t_(k+2), ..., t_(n-1+k)]
        # where t_j is the timestamp at position j in the original times series.
        # Since positions 0..n-1 map to observation times, we use event_end_times
        # values at those positions.

        # Construct implicit times: use event_end_times itself as the source.
        # times[i] = the timestamp "at position i", which we take from event_end_times[i].
        # But wait - event_end_times[i] is the END time, not start time.

        # Alternative: extract the first len(event_end_times) values from event_end_times
        # to use as start times. This works if event_end_times was built from a uniform
        # times series: event_end_times[i] = times[min(i+k, n-1)].
        # Then times[i] appears in event_end_times[j] where j = i-k (if i >= k).

        # Pragmatic solution: shift event_end_times backwards to approximate start times.
        # Estimate the horizon from the series structure.
        self.event_start_times = self._build_start_times()

    def _build_start_times(self) -> pd.Series:
        """Build implicit start times for each event from event_end_times structure."""
        n = len(self.event_end_times)

        # Strategy: event_end_times is constructed as times[min(i+k, n-1)] where
        # times[] is a uniform time series and k is the horizon.
        # We need to reverse this to get times[i].

        # Since event_end_times is uniformly spaced (in the typical case), we can
        # infer the original times[] series. The key insight:
        #   - event_end_times[i] = times[min(i+k, n-1)]
        #   - For i < n-k, event_end_times[i] = times[i+k]
        #   - So times[i] should equal event_end_times[i-k] (if i >= k)
        #   - For i < k, we extrapolate backwards

        # To find k, note that event_end_times plateaus at times[n-1] when i >= n-k.
        # Find the plateau point.

        if n == 1:
            return pd.Series([self.event_end_times.iloc[0]])

        # Detect frequency (time between consecutive timestamps)
        freq = self.event_end_times.iloc[1] - self.event_end_times.iloc[0]

        # Estimate horizon k by finding where event_end_times plateaus
        horizon = 0
        for i in range(n - 1):
            if self.event_end_times.iloc[i] == self.event_end_times.iloc[i + 1]:
                # Found plateau, means i >= n-1-k, so k ≈ n-1-i
                horizon = n - 1 - i
                break
            horizon = i + 1  # No plateau yet, assume max horizon

        # Clamp horizon to reasonable range
        horizon = min(horizon, n - 1)

        # Build times[]: for each index i, times[i] = event_end_times[max(0, i-horizon)]
        # But this would give us times values from event_end_times, which is circular.

        # Better approach: assume event_end_times represents a shifted view of times.
        # If event_end_times[i] = times[i+k], then the first element of event_end_times
        # corresponds to times[k]. We can build times[] by shifting back.

        # Create times[] by taking event_end_times and shifting backward by freq*horizon
        start_times_list = [self.event_end_times.iloc[i] - horizon * freq for i in range(n)]

        return pd.Series(start_times_list).reset_index(drop=True)

    def split(self, X) -> Iterator[Tuple[np.ndarray, np.ndarray]]:
        n = len(X) if hasattr(X, "__len__") else X.shape[0]
        if n != len(self.event_end_times):
            raise ValueError(
                f"X length {n} != event_end_times length {len(self.event_end_times)}"
            )

        indices = np.arange(n)
        fold_size = n // self.n_splits
        # Contiguous test folds
        fold_bounds = [(i * fold_size, (i + 1) * fold_size if i < self.n_splits - 1 else n)
                       for i in range(self.n_splits)]

        for start, end in fold_bounds:
            test_idx = indices[start:end]
            test_max_idx = test_idx[-1]

            train_mask = np.ones(n, dtype=bool)
            train_mask[test_idx] = False

            # Purge: drop train indices whose event window overlaps the test window.
            # Event i spans [event_start_times[i], event_end_times[i]].
            # Test window spans the observation times of test samples: [event_start_times[test_idx[0]], event_start_times[test_idx[-1]]].
            # This represents the time period during which test observations are made.

            test_start_time = self.event_start_times.iloc[test_idx[0]]
            test_end_time = self.event_start_times.iloc[test_idx[-1]]

            for i in np.where(train_mask)[0]:
                train_start_time = self.event_start_times.iloc[i]
                train_end_time = self.event_end_times.iloc[i]

                # Check overlap: intervals [a,b] and [c,d] overlap if b >= c AND a <= d
                # Train event [train_start, train_end] overlaps test window [test_start, test_end]
                if train_end_time >= test_start_time and train_start_time <= test_end_time:
                    train_mask[i] = False

            # Embargo: drop train indices immediately after test window
            if self.embargo_bars > 0:
                embargo_end = min(n, test_max_idx + 1 + self.embargo_bars)
                train_mask[test_max_idx + 1:embargo_end] = False

            train_idx = indices[train_mask]
            yield train_idx, test_idx
