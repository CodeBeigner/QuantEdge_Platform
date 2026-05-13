"""Tests for purged K-fold time-series CV (Lopez de Prado Ch. 7)."""
import numpy as np
import pandas as pd
import pytest

from ml_models.purged_kfold import PurgedKFold


def _mock_times(n=100):
    return pd.Series(pd.date_range("2024-01-01", periods=n, freq="1h", tz="UTC"))


def _mock_event_windows(times, horizon_bars=5):
    """Uniform-horizon synthetic events. Real data has variable horizons
    (covered by test_purge_works_with_variable_horizons below)."""
    start_times = pd.Series(times.to_numpy(), dtype=times.dtype)
    outcome_idx = np.minimum(np.arange(len(times)) + horizon_bars, len(times) - 1)
    end_times = pd.Series(times.iloc[outcome_idx].to_numpy(), dtype=times.dtype)
    return start_times, end_times


def test_splits_are_disjoint():
    times = _mock_times(100)
    starts, ends = _mock_event_windows(times, horizon_bars=5)
    cv = PurgedKFold(n_splits=5, event_start_times=starts, event_end_times=ends, embargo_bars=2)
    all_test_sets = [set(test_idx) for _, test_idx in cv.split(np.arange(100))]
    total_test = set().union(*all_test_sets)
    assert sum(len(s) for s in all_test_sets) == len(total_test)


def test_purge_removes_leaky_train_samples():
    times = _mock_times(100)
    starts, ends = _mock_event_windows(times, horizon_bars=5)
    cv = PurgedKFold(n_splits=5, event_start_times=starts, event_end_times=ends, embargo_bars=0)
    for train_idx, test_idx in cv.split(np.arange(100)):
        test_times = times.iloc[test_idx]
        for ti in train_idx:
            # A train event ending inside the test window is leakage — must not appear
            if ends.iloc[ti] >= test_times.min() and starts.iloc[ti] <= test_times.max():
                pytest.fail(f"Train index {ti} leaks into test window")


def test_embargo_removes_samples_after_test():
    times = _mock_times(100)
    starts, ends = _mock_event_windows(times, horizon_bars=5)
    cv = PurgedKFold(n_splits=4, event_start_times=starts, event_end_times=ends, embargo_bars=3)
    for train_idx, test_idx in cv.split(np.arange(100)):
        test_max = max(test_idx)
        for ti in train_idx:
            assert not (test_max < ti <= test_max + 3)


def test_n_splits_produces_correct_fold_count():
    times = _mock_times(100)
    starts, ends = _mock_event_windows(times)
    cv = PurgedKFold(n_splits=5, event_start_times=starts, event_end_times=ends, embargo_bars=0)
    folds = list(cv.split(np.arange(100)))
    assert len(folds) == 5


def test_purge_works_with_variable_horizons():
    """Real triple-barrier output has variable horizons per signal. Make sure
    the purge detects overlap using explicit start+end times, not a horizon
    heuristic."""
    times = _mock_times(100)
    # Manually construct variable-horizon events: some short, some long.
    horizons = [3, 17, 5, 24, 8] * 20  # 100 entries, mixed short/long
    starts = pd.Series(times.to_numpy(), dtype=times.dtype)
    ends = pd.Series(
        [times.iloc[min(i + h, 99)] for i, h in enumerate(horizons)],
        dtype=times.dtype,
    )

    cv = PurgedKFold(n_splits=5, event_start_times=starts, event_end_times=ends, embargo_bars=0)
    for train_idx, test_idx in cv.split(np.arange(100)):
        test_window_start = starts.iloc[test_idx[0]]
        test_window_end = ends.iloc[test_idx[-1]]
        for ti in train_idx:
            # Train event must not overlap test window [test_window_start, test_window_end]
            train_start, train_end = starts.iloc[ti], ends.iloc[ti]
            overlaps = train_start <= test_window_end and train_end >= test_window_start
            assert not overlaps, (
                f"Train sample {ti} overlaps test window "
                f"[{test_window_start}, {test_window_end}]"
            )


def test_rejects_mismatched_length():
    times = _mock_times(100)
    starts = pd.Series(times.to_numpy()[:50], dtype=times.dtype)
    ends = pd.Series(times.to_numpy(), dtype=times.dtype)
    with pytest.raises(ValueError, match="length"):
        PurgedKFold(n_splits=5, event_start_times=starts, event_end_times=ends)


def test_rejects_n_splits_below_2():
    times = _mock_times(10)
    starts, ends = _mock_event_windows(times, horizon_bars=2)
    with pytest.raises(ValueError, match="n_splits"):
        PurgedKFold(n_splits=1, event_start_times=starts, event_end_times=ends)
