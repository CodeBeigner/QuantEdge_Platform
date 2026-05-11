"""Tests for purged K-fold time-series CV (Lopez de Prado Ch. 7)."""
import numpy as np
import pandas as pd
import pytest

from ml_models.purged_kfold import PurgedKFold


def _mock_times(n=100):
    return pd.Series(pd.date_range("2024-01-01", periods=n, freq="1h", tz="UTC"))


def _mock_event_times(times, horizon_bars=5):
    """For each event at times[i], outcome arrives at times[i+horizon_bars]."""
    outcome_idx = np.arange(len(times)) + horizon_bars
    outcome_idx = np.minimum(outcome_idx, len(times) - 1)
    # Use .to_numpy() to preserve timezone, or construct without .values
    return pd.Series(times.iloc[outcome_idx].to_numpy(), index=times.index, dtype=times.dtype)


def test_splits_are_disjoint():
    times = _mock_times(100)
    events = _mock_event_times(times, horizon_bars=5)
    cv = PurgedKFold(n_splits=5, event_end_times=events, embargo_bars=2)
    all_test_sets = []
    for _, test_idx in cv.split(np.arange(100)):
        all_test_sets.append(set(test_idx))
    total_test = set().union(*all_test_sets)
    # No overlap between test folds
    assert sum(len(s) for s in all_test_sets) == len(total_test)


def test_purge_removes_leaky_train_samples():
    times = _mock_times(100)
    events = _mock_event_times(times, horizon_bars=5)
    cv = PurgedKFold(n_splits=5, event_end_times=events, embargo_bars=0)
    for train_idx, test_idx in cv.split(np.arange(100)):
        test_times = times.iloc[test_idx]
        for ti in train_idx:
            # A train event ending inside the test window is leakage — must not appear
            if events.iloc[ti] >= test_times.min() and times.iloc[ti] <= test_times.max():
                pytest.fail(f"Train index {ti} leaks into test window")


def test_embargo_removes_samples_after_test():
    times = _mock_times(100)
    events = _mock_event_times(times, horizon_bars=5)
    cv = PurgedKFold(n_splits=4, event_end_times=events, embargo_bars=3)
    for train_idx, test_idx in cv.split(np.arange(100)):
        test_max = max(test_idx)
        # Embargo: no train index in (test_max, test_max + embargo_bars]
        for ti in train_idx:
            assert not (test_max < ti <= test_max + 3)


def test_n_splits_produces_correct_fold_count():
    times = _mock_times(100)
    events = _mock_event_times(times)
    cv = PurgedKFold(n_splits=5, event_end_times=events, embargo_bars=0)
    folds = list(cv.split(np.arange(100)))
    assert len(folds) == 5
