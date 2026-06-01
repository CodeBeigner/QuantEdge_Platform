"""Meta-labeler: XGBoost binary classifier on triple-barrier outcomes.

Input: a feature frame enriched with derivatives features (funding, OI) and
a primary-signal direction column. Label: 1 if primary signal hit TP first,
0 if SL first. Rows labeled -1 (timeout, no clear outcome) are dropped.

The model's output probability is used downstream by the trading gate:
trades with meta_prob < threshold are vetoed.
"""
from __future__ import annotations

import logging
from dataclasses import dataclass, field
from typing import Any, Dict, List, Optional

import numpy as np
import pandas as pd
try:
    from xgboost import XGBClassifier
except Exception:
    XGBClassifier = None
    logging.getLogger("ml.meta_labeler").warning("xgboost not available (missing libomp)")

log = logging.getLogger("ml.meta_labeler")


@dataclass
class MetaLabeler:
    feature_cols: List[str]
    n_estimators: int = 200
    max_depth: int = 4
    learning_rate: float = 0.05
    subsample: float = 0.8
    colsample_bytree: float = 0.8
    random_state: int = 42

    _model: Optional[XGBClassifier] = field(default=None, init=False, repr=False)
    _trained: bool = field(default=False, init=False, repr=False)

    def train(self, df: pd.DataFrame, label_col: str = "label") -> Dict[str, Any]:
        mask = df[label_col].isin([0, 1])
        n_total = len(df)
        n_kept = int(mask.sum())
        if n_kept < 50:
            raise ValueError(f"not enough binary labels ({n_kept}); need >= 50")

        if XGBClassifier is None:
            raise RuntimeError("xgboost not available (missing libomp). Install: brew install libomp")

        frame = df.loc[mask].copy()
        X = frame[self.feature_cols]
        y = frame[label_col].astype(int)

        self._model = XGBClassifier(
            n_estimators=self.n_estimators,
            max_depth=self.max_depth,
            learning_rate=self.learning_rate,
            subsample=self.subsample,
            colsample_bytree=self.colsample_bytree,
            random_state=self.random_state,
            eval_metric="logloss",
            tree_method="hist",
        )
        self._model.fit(X, y)
        self._trained = True

        train_pred = self._model.predict(X)
        train_acc = float(np.mean(train_pred == y.values))

        return {
            "n_train": n_kept,
            "n_dropped_timeout": n_total - n_kept,
            "train_accuracy": train_acc,
            "feature_cols": self.feature_cols,
        }

    def predict(self, features: pd.DataFrame) -> Dict[str, Any]:
        if not self._trained or self._model is None:
            raise RuntimeError("MetaLabeler not trained; call train() first")
        X = features[self.feature_cols]
        prob = float(self._model.predict_proba(X)[0, 1])
        direction = int(features["direction"].iloc[0]) if "direction" in features.columns else 0
        return {"meta_prob": prob, "direction": direction}
