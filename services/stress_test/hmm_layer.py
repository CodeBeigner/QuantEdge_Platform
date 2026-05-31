"""Hidden Markov Model for market regime detection."""
from __future__ import annotations

import logging
from typing import List, Optional, Tuple

import numpy as np
from hmmlearn.hmm import GaussianHMM

_log = logging.getLogger(__name__)

REGIME_NAMES = {0: "bear", 1: "sideways", 2: "bull"}


def fit_hmm(returns: np.ndarray, n_states: int = 3) -> Tuple[GaussianHMM, np.ndarray, List[str]]:
    if len(returns) < 30:
        raise ValueError(f"Need at least 30 data points, got {len(returns)}")

    X = returns.reshape(-1, 1)

    model = GaussianHMM(
        n_components=n_states,
        covariance_type="diag",
        n_iter=1000,
        random_state=42,
    )
    model.fit(X)

    hidden_states = model.predict(X)

    means = model.means_.flatten()
    sorted_indices = np.argsort(means)
    mapping = {old: new for new, old in enumerate(sorted_indices)}

    remapped_states = np.array([mapping[s] for s in hidden_states])
    regimes = [_regime_label(s, n_states) for s in remapped_states]

    _log.info(
        "HMM fitted: %d states, means=%s, converged=%s",
        n_states,
        np.round(means[sorted_indices], 4).tolist(),
        model.monitor_.converged,
    )

    return model, model.predict_proba(X), regimes


def _regime_label(state: int, n_states: int) -> str:
    if n_states == 2:
        return "bear" if state == 0 else "bull"
    if state == 0:
        return "bear"
    elif state == n_states - 1:
        return "bull"
    return "sideways"


def get_transition_matrix(model: GaussianHMM) -> List[List[float]]:
    return np.round(model.transmat_, 4).tolist()


def regime_performance(
    returns: np.ndarray,
    regimes: List[str],
) -> dict:
    perfs = {}
    order = ["bear", "sideways", "bull"]
    unique_regimes = sorted(set(regimes), key=lambda r: order.index(r) if r in order else 0)

    for r in unique_regimes:
        mask = np.array([x == r for x in regimes])
        regime_returns = returns[mask]
        if len(regime_returns) == 0:
            perfs[r] = {"avg_return": 0.0, "win_rate": 0.0}
        else:
            perfs[r] = {
                "avg_return": round(float(np.mean(regime_returns)), 6),
                "win_rate": round(float(np.sum(regime_returns > 0) / len(regime_returns)), 4),
            }

    return perfs
