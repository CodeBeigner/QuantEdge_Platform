"""Regime Guard — shared service wrapping stress_test/hmm_layer.py for live signal regime assignment."""
from __future__ import annotations

import logging
from typing import List, Tuple

import numpy as np
from hmmlearn.hmm import GaussianHMM

from services.stress_test.hmm_layer import fit_hmm, regime_performance, get_transition_matrix

_log = logging.getLogger(__name__)

REGIME_NAMES = ["bear", "sideways", "bull"]


def classify_regime(returns: np.ndarray) -> Tuple[str, GaussianHMM]:
    """Classify the current market regime from returns. Returns (regime_name, model)."""
    if len(returns) < 30:
        return "sideways", None
    model, _, regimes = fit_hmm(returns, n_states=3)
    current = regimes[-1]
    return current, model


def regime_transition_risk(returns: np.ndarray) -> float:
    """Probability that the market will transition out of the current regime next period."""
    if len(returns) < 30:
        return 0.0
    model, _, regimes = fit_hmm(returns, n_states=3)
    current_state = regimes[-1]
    name_to_idx = {"bear": 0, "sideways": 1, "bull": 2}
    idx = name_to_idx.get(current_state, 1)
    stay_prob = model.transmat_[idx, idx]
    return round(1.0 - float(stay_prob), 4)


def is_adverse_regime(regime: str, strategy_style: str) -> bool:
    """Check if a regime is adverse for a given strategy style."""
    if strategy_style == "MEAN_REVERSION":
        return regime in ["bull", "bear"]
    if strategy_style == "TREND_FOLLOWING":
        return regime == "sideways"
    return False
