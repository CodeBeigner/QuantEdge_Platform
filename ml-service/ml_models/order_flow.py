"""Order-flow model: LightGBM directional classifier on microstructure fallback features.

Why fallback: full L2 book reconstruction is out of scope at retail capital. The
fallback set is documented (~70% of edge retained) and uses features derivable
from bar-level OHLCV plus derivatives data we already ingest:
    - CVD proxy: signed volume where sign comes from candle direction
    - Aggressive-buy-ratio: fraction of up-bars in rolling window
    - Funding rate and its delta
    - Open interest deltas (1-bar and 4-bar)
    - Perp-spot basis proxy: using bar close vs recent rolling mean

Label: direction of the next `forward_bars`-bar return.
"""
from __future__ import annotations

import logging
from dataclasses import dataclass, field
from typing import Any, Dict, List, Optional

import numpy as np
import pandas as pd
from lightgbm import LGBMClassifier

log = logging.getLogger("ml.order_flow")

FLOW_FEATURE_COLS: List[str] = [
    "cvd",
    "aggressive_buy_ratio_20",
    "funding_rate",
    "funding_rate_delta",
    "oi_delta_1",
    "oi_delta_4",
    "basis_proxy",
]


def compute_flow_features(bars: pd.DataFrame) -> pd.DataFrame:
    """Build fallback microstructure features from bar OHLCV + derivatives cols.

    Callers should pre-enrich bars with `feature_enrichment.enrich_with_derivatives`.
    """
    out = bars.copy().sort_values("time").reset_index(drop=True)

    close = out["close"].astype(float)
    volume = out["volume"].astype(float)

    # CVD proxy: cumulative signed volume; up-bar = +volume, down-bar = -volume
    direction = np.sign(close.diff().fillna(0.0))
    signed_vol = direction * volume
    out["cvd"] = signed_vol.cumsum()

    # Aggressive buy ratio: fraction of up-bars in trailing 20-bar window
    up = (direction > 0).astype(float)
    out["aggressive_buy_ratio_20"] = up.rolling(20, min_periods=5).mean().fillna(0.5)

    # Basis proxy: normalized distance of current close from 20-bar mean
    ma20 = close.rolling(20, min_periods=1).mean()
    out["basis_proxy"] = ((close - ma20) / ma20).fillna(0.0)

    # Pass-through enrichment columns (ensure they exist; default to 0)
    for col in ["funding_rate", "funding_rate_delta", "oi_delta_1", "oi_delta_4"]:
        if col not in out.columns:
            out[col] = 0.0

    return out


@dataclass
class OrderFlowModel:
    n_estimators: int = 200
    max_depth: int = -1
    num_leaves: int = 31
    learning_rate: float = 0.05
    random_state: int = 42
    min_confidence: float = 0.55

    _model: Optional[LGBMClassifier] = field(default=None, init=False, repr=False)
    _trained: bool = field(default=False, init=False, repr=False)

    def train(self, enriched_bars: pd.DataFrame, forward_bars: int = 4) -> Dict[str, Any]:
        feat = compute_flow_features(enriched_bars)
        close = feat["close"].astype(float)
        forward_ret = close.shift(-forward_bars) / close - 1.0
        y = np.sign(forward_ret)
        # Strict future data: y[-forward_bars:] is NaN — drop those
        mask = y.notna()
        X = feat.loc[mask, FLOW_FEATURE_COLS]
        y = y.loc[mask].astype(int)
        # Map {-1, 0, 1} → {0, 1, 2} for classifier
        y_mapped = y + 1

        if len(X) < 50:
            raise ValueError(f"not enough rows ({len(X)}); need >= 50")

        self._model = LGBMClassifier(
            n_estimators=self.n_estimators,
            max_depth=self.max_depth,
            num_leaves=self.num_leaves,
            learning_rate=self.learning_rate,
            random_state=self.random_state,
            verbose=-1,
        )
        self._model.fit(X, y_mapped)
        self._trained = True

        pred = self._model.predict(X)
        train_acc = float(np.mean(pred == y_mapped.values))
        return {
            "n_train": len(X),
            "train_accuracy": train_acc,
            "feature_cols": FLOW_FEATURE_COLS,
            "forward_bars": forward_bars,
        }

    def predict(self, feat_row: pd.DataFrame) -> Dict[str, Any]:
        if not self._trained or self._model is None:
            raise RuntimeError("OrderFlowModel not trained")
        X = feat_row[FLOW_FEATURE_COLS].tail(1)
        probs = self._model.predict_proba(X)[0]

        # Handle case where not all classes were present in training
        # LightGBM classes_ attribute tells us which classes were fitted
        fitted_classes = self._model.classes_
        full_probs = np.zeros(3)  # Always return 3 classes: 0, 1, 2
        for i, cls in enumerate(fitted_classes):
            full_probs[cls] = probs[i]

        # Class order: 0 → short, 1 → flat, 2 → long
        conf = float(full_probs.max())
        cls = int(np.argmax(full_probs)) - 1  # back to {-1, 0, 1}
        direction = cls if conf >= self.min_confidence else 0
        return {
            "flow_score": conf,
            "direction": direction,
            "probs": {
                "short": float(full_probs[0]),
                "flat":  float(full_probs[1]),
                "long":  float(full_probs[2]),
            },
        }
