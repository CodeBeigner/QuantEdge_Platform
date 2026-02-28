"""
XGBoost Signal Model — Predicts next-day return direction.

Trains on historical technical indicators and predicts
BUY / SELL / HOLD signals with confidence scores.
"""
import numpy as np
import pandas as pd
from xgboost import XGBClassifier
from sklearn.model_selection import train_test_split
from sklearn.metrics import accuracy_score
from feature_engine import compute_features, FEATURE_COLS
from typing import Optional


class SignalModel:
    """XGBoost-based signal prediction model."""

    def __init__(self):
        self.model: Optional[XGBClassifier] = None
        self.accuracy: float = 0.0
        self.is_trained: bool = False

    def train(self, df: pd.DataFrame) -> dict:
        """Train the model on OHLCV data.
        
        Returns training metrics.
        """
        featured = compute_features(df)
        featured = featured.dropna(subset=FEATURE_COLS + ['target'])

        if len(featured) < 100:
            return {"error": "Not enough data (need 100+ rows)", "rows": len(featured)}

        X = featured[FEATURE_COLS].values
        y = featured['target'].values

        X_train, X_test, y_train, y_test = train_test_split(
            X, y, test_size=0.2, shuffle=False  # Time-ordered split
        )

        self.model = XGBClassifier(
            n_estimators=100,
            max_depth=4,
            learning_rate=0.1,
            subsample=0.8,
            colsample_bytree=0.8,
            use_label_encoder=False,
            eval_metric='logloss',
            random_state=42,
        )
        self.model.fit(X_train, y_train)

        train_acc = accuracy_score(y_train, self.model.predict(X_train))
        test_acc = accuracy_score(y_test, self.model.predict(X_test))
        self.accuracy = test_acc
        self.is_trained = True

        # Feature importance
        importance = dict(zip(FEATURE_COLS, self.model.feature_importances_.tolist()))

        return {
            "status": "trained",
            "train_accuracy": round(train_acc, 4),
            "test_accuracy": round(test_acc, 4),
            "samples_train": len(X_train),
            "samples_test": len(X_test),
            "feature_importance": importance,
        }

    def predict(self, df: pd.DataFrame) -> dict:
        """Predict signal for the latest data point.
        
        Returns signal (BUY/SELL/HOLD), confidence, and features.
        """
        if not self.is_trained or self.model is None:
            return {"error": "Model not trained yet"}

        featured = compute_features(df)
        featured = featured.dropna(subset=FEATURE_COLS)

        if len(featured) == 0:
            return {"error": "Not enough data to compute features"}

        latest = featured.iloc[-1]
        X = latest[FEATURE_COLS].values.reshape(1, -1)

        proba = self.model.predict_proba(X)[0]
        pred = self.model.predict(X)[0]

        # Convert to signal: 1=BUY, 0=SELL, middle=HOLD
        confidence = float(max(proba))
        if confidence < 0.55:
            signal = "HOLD"
        elif pred == 1:
            signal = "BUY"
        else:
            signal = "SELL"

        features = {col: round(float(latest[col]), 4) for col in FEATURE_COLS}

        return {
            "signal": signal,
            "confidence": round(confidence, 4),
            "direction_prob": {"up": round(float(proba[1]), 4), "down": round(float(proba[0]), 4)},
            "features": features,
            "model_accuracy": self.accuracy,
        }
