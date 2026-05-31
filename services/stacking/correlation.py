"""Pairwise correlation matrix for strategy returns."""
from __future__ import annotations

import logging
from typing import Dict, List, Tuple

import numpy as np
import pandas as pd

_log = logging.getLogger(__name__)


def pair_matrix(strategies: Dict[str, List[float]]) -> Dict:
    """Compute pairwise return correlation matrix for all strategies.

    Returns dict with keys: labels, matrix (list of lists), flagged_pairs.
    Flags pairs with correlation > 0.65.
    """
    if not strategies:
        return {"labels": [], "matrix": [], "flagged_pairs": [], "n_strategies": 0}
    if len(strategies) == 1:
        name = list(strategies.keys())[0]
        return {"labels": [name], "matrix": [[1.0]], "flagged_pairs": [], "n_strategies": 1}

    names = sorted(strategies.keys())
    n = len(names)
    corr = np.zeros((n, n))

    for i in range(n):
        for j in range(n):
            if i == j:
                corr[i, j] = 1.0
                continue
            ri = np.array(strategies[names[i]])
            rj = np.array(strategies[names[j]])
            min_len = min(len(ri), len(rj))
            if min_len < 2:
                corr[i, j] = 0.0
            else:
                corr[i, j] = round(float(np.corrcoef(ri[:min_len], rj[:min_len])[0, 1]), 4)

    flagged = []
    for i in range(n):
        for j in range(i + 1, n):
            if abs(corr[i, j]) > 0.65:
                flagged.append({
                    "pair": [names[i], names[j]],
                    "correlation": corr[i, j],
                    "severity": "high" if abs(corr[i, j]) > 0.85 else "moderate",
                })

    return {
        "labels": names,
        "matrix": corr.round(4).tolist(),
        "flagged_pairs": flagged,
        "flagged_count": len(flagged),
        "n_strategies": n,
    }


def correlation_heatmap_data(strategies: Dict[str, List[float]]) -> List[dict]:
    """Generate heatmap-ready data: [{id, data: [{x, y, value}]}] for Nivo/Recharts."""
    result = pair_matrix(strategies)
    labels = result["labels"]
    matrix = result["matrix"]

    data = []
    for i, label_i in enumerate(labels):
        for j, label_j in enumerate(labels):
            data.append({
                "x": label_i,
                "y": label_j,
                "value": matrix[i][j],
            })

    return data


def detect_concentration_risk(strategies: Dict[str, float]) -> List[dict]:
    """Flag strategies that exceed allocation concentration thresholds."""
    warnings = []
    total = sum(strategies.values())
    if total == 0:
        return warnings

    for name, alloc in strategies.items():
        if alloc > 15.0:
            warnings.append({
                "strategy": name,
                "allocation_pct": round(alloc, 1),
                "threshold_pct": 15.0,
                "message": f"{name} at {alloc:.1f}% exceeds 15% hard cap",
            })

    return warnings
