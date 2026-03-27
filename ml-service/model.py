"""
XGBoost + LSTM Signal Models — Predicts next-day return direction.

- SignalModel: XGBoost classifier on technical indicators
- LSTMSignalModel: LSTM neural network on price sequences
"""
import os
from datetime import datetime, timezone
from typing import Optional

import joblib
import numpy as np
import pandas as pd
from xgboost import XGBClassifier
from sklearn.model_selection import train_test_split
from sklearn.metrics import accuracy_score
from feature_engine import compute_features, FEATURE_COLS

# ── Constants ────────────────────────────────────────────────
MIN_TRAINING_SAMPLES = 100
HOLD_CONFIDENCE_THRESHOLD = 0.55
XGBOOST_N_ESTIMATORS = 100
XGBOOST_MAX_DEPTH = 4
XGBOOST_LEARNING_RATE = 0.1
XGBOOST_SUBSAMPLE = 0.8
XGBOOST_COLSAMPLE = 0.8
XGBOOST_RANDOM_STATE = 42

LSTM_HIDDEN_SIZE = 64
LSTM_NUM_LAYERS = 2
LSTM_SEQUENCE_LENGTH = 20
LSTM_EPOCHS = 50
LSTM_BATCH_SIZE = 32
LSTM_LEARNING_RATE = 0.001


class SignalModel:
    """XGBoost-based signal prediction model."""

    def __init__(self):
        self.model: Optional[XGBClassifier] = None
        self.accuracy: float = 0.0
        self.is_trained: bool = False
        self.trained_date: Optional[str] = None

    def train(self, df: pd.DataFrame) -> dict:
        """Train the model on OHLCV data.

        Returns training metrics.
        """
        featured = compute_features(df)
        featured = featured.dropna(subset=FEATURE_COLS + ['target'])

        if len(featured) < MIN_TRAINING_SAMPLES:
            return {"error": f"Not enough data (need {MIN_TRAINING_SAMPLES}+ rows)", "rows": len(featured)}

        X = featured[FEATURE_COLS].values
        y = featured['target'].values

        X_train, X_test, y_train, y_test = train_test_split(
            X, y, test_size=0.2, shuffle=False  # Time-ordered split
        )

        self.model = XGBClassifier(
            n_estimators=XGBOOST_N_ESTIMATORS,
            max_depth=XGBOOST_MAX_DEPTH,
            learning_rate=XGBOOST_LEARNING_RATE,
            subsample=XGBOOST_SUBSAMPLE,
            colsample_bytree=XGBOOST_COLSAMPLE,
            use_label_encoder=False,
            eval_metric='logloss',
            random_state=XGBOOST_RANDOM_STATE,
        )
        self.model.fit(X_train, y_train)

        train_acc = accuracy_score(y_train, self.model.predict(X_train))
        test_acc = accuracy_score(y_test, self.model.predict(X_test))
        self.accuracy = test_acc
        self.is_trained = True
        self.trained_date = datetime.now(timezone.utc).isoformat()

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
        if confidence < HOLD_CONFIDENCE_THRESHOLD:
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


    def save_model(self, symbol: str, model_dir: str = 'models/') -> dict:
        """Persist the trained XGBoost model and metadata to disk."""
        if not self.is_trained or self.model is None:
            return {"error": "Model not trained yet"}

        os.makedirs(model_dir, exist_ok=True)
        path = os.path.join(model_dir, f"{symbol}_xgb.joblib")
        meta = {
            "model": self.model,
            "accuracy": self.accuracy,
            "trained_date": self.trained_date,
            "features": FEATURE_COLS,
        }
        joblib.dump(meta, path)
        return {"status": "saved", "path": path, "symbol": symbol}

    def load_model(self, symbol: str, model_dir: str = 'models/') -> dict:
        """Load a previously saved XGBoost model from disk."""
        path = os.path.join(model_dir, f"{symbol}_xgb.joblib")
        if not os.path.exists(path):
            return {"error": f"No saved model found at {path}"}

        meta = joblib.load(path)
        self.model = meta["model"]
        self.accuracy = meta["accuracy"]
        self.trained_date = meta.get("trained_date")
        self.is_trained = True
        return {"status": "loaded", "path": path, "symbol": symbol, "accuracy": self.accuracy}

    def walk_forward_validate(self, df: pd.DataFrame, n_splits: int = 5) -> dict:
        """Expanding-window time-series cross-validation.

        Returns per-fold accuracy and signal-based Sharpe ratio.
        """
        featured = compute_features(df)
        featured = featured.dropna(subset=FEATURE_COLS + ['target'])

        if len(featured) < MIN_TRAINING_SAMPLES * 2:
            return {"error": f"Not enough data for {n_splits}-fold walk-forward"}

        X = featured[FEATURE_COLS].values
        y = featured['target'].values
        returns = featured['returns_1d'].values

        n = len(X)
        fold_size = n // (n_splits + 1)
        results = []

        for fold in range(n_splits):
            train_end = fold_size * (fold + 2)
            test_start = train_end
            test_end = min(test_start + fold_size, n)

            if test_end <= test_start:
                break

            X_train, y_train = X[:train_end], y[:train_end]
            X_test, y_test = X[test_start:test_end], y[test_start:test_end]
            fold_returns = returns[test_start:test_end]

            clf = XGBClassifier(
                n_estimators=XGBOOST_N_ESTIMATORS,
                max_depth=XGBOOST_MAX_DEPTH,
                learning_rate=XGBOOST_LEARNING_RATE,
                subsample=XGBOOST_SUBSAMPLE,
                colsample_bytree=XGBOOST_COLSAMPLE,
                use_label_encoder=False,
                eval_metric='logloss',
                random_state=XGBOOST_RANDOM_STATE,
            )
            clf.fit(X_train, y_train)

            preds = clf.predict(X_test)
            acc = accuracy_score(y_test, preds)

            # Signal-based Sharpe: go long when pred=1, short when pred=0
            signal_returns = np.where(preds == 1, fold_returns, -fold_returns)
            signal_returns = signal_returns[np.isfinite(signal_returns)]
            sharpe = 0.0
            if len(signal_returns) > 1 and np.std(signal_returns) > 0:
                sharpe = float(np.mean(signal_returns) / np.std(signal_returns) * np.sqrt(252))

            results.append({
                "fold": fold + 1,
                "train_size": len(X_train),
                "test_size": len(X_test),
                "accuracy": round(acc, 4),
                "sharpe": round(sharpe, 4),
            })

        avg_acc = round(float(np.mean([r["accuracy"] for r in results])), 4)
        avg_sharpe = round(float(np.mean([r["sharpe"] for r in results])), 4)

        return {
            "n_splits": len(results),
            "folds": results,
            "avg_accuracy": avg_acc,
            "avg_sharpe": avg_sharpe,
        }


# ── LSTM Network (module-level for pickling) ─────────────────
try:
    import torch
    import torch.nn as nn

    class LSTMNet(nn.Module):
        def __init__(self, input_size: int, hidden_size: int = LSTM_HIDDEN_SIZE,
                     num_layers: int = LSTM_NUM_LAYERS):
            super().__init__()
            self.lstm = nn.LSTM(
                input_size=input_size,
                hidden_size=hidden_size,
                num_layers=num_layers,
                batch_first=True,
                dropout=0.2,
            )
            self.fc = nn.Linear(hidden_size, 2)

        def forward(self, x):
            lstm_out, _ = self.lstm(x)
            return self.fc(lstm_out[:, -1, :])

    _TORCH_AVAILABLE = True
except ImportError:
    _TORCH_AVAILABLE = False


class LSTMSignalModel:
    """LSTM neural network for time-series signal prediction.

    Uses PyTorch to build a multi-layer LSTM that processes
    sequences of close prices and technical features.
    """

    def __init__(self):
        self.model = None
        self.scaler = None
        self.accuracy: float = 0.0
        self.is_trained: bool = False
        self.trained_date: Optional[str] = None

    def train(self, df: pd.DataFrame) -> dict:
        """Train the LSTM model on OHLCV data.

        Returns training metrics.
        """
        if not _TORCH_AVAILABLE:
            return {"error": "PyTorch not installed. Run: pip install torch"}

        from sklearn.preprocessing import StandardScaler

        featured = compute_features(df)
        featured = featured.dropna(subset=FEATURE_COLS + ['target'])

        if len(featured) < MIN_TRAINING_SAMPLES:
            return {"error": f"Not enough data (need {MIN_TRAINING_SAMPLES}+ rows)"}

        # Prepare sequences
        X_raw = featured[FEATURE_COLS].values
        y_raw = featured['target'].values

        self.scaler = StandardScaler()
        X_scaled = self.scaler.fit_transform(X_raw)

        X_seq, y_seq = [], []
        for i in range(LSTM_SEQUENCE_LENGTH, len(X_scaled)):
            X_seq.append(X_scaled[i - LSTM_SEQUENCE_LENGTH:i])
            y_seq.append(y_raw[i])

        X_seq = np.array(X_seq)
        y_seq = np.array(y_seq)

        # Train/test split (time-ordered)
        split = int(len(X_seq) * 0.8)
        X_train = torch.FloatTensor(X_seq[:split])
        y_train = torch.LongTensor(y_seq[:split])
        X_test = torch.FloatTensor(X_seq[split:])
        y_test = torch.LongTensor(y_seq[split:])

        # Build LSTM
        input_size = len(FEATURE_COLS)
        model = LSTMNet(input_size)
        criterion = torch.nn.CrossEntropyLoss()
        optimizer = torch.optim.Adam(model.parameters(), lr=LSTM_LEARNING_RATE)

        # Training loop
        model.train()
        for epoch in range(LSTM_EPOCHS):
            for i in range(0, len(X_train), LSTM_BATCH_SIZE):
                batch_X = X_train[i:i + LSTM_BATCH_SIZE]
                batch_y = y_train[i:i + LSTM_BATCH_SIZE]

                optimizer.zero_grad()
                output = model(batch_X)
                loss = criterion(output, batch_y)
                loss.backward()
                optimizer.step()

        # Evaluate
        model.eval()
        with torch.no_grad():
            train_pred = model(X_train).argmax(dim=1).numpy()
            test_pred = model(X_test).argmax(dim=1).numpy()

        train_acc = accuracy_score(y_train.numpy(), train_pred)
        test_acc = accuracy_score(y_test.numpy(), test_pred)

        self.model = model
        self.accuracy = test_acc
        self.is_trained = True
        self.trained_date = datetime.now(timezone.utc).isoformat()

        return {
            "status": "trained",
            "model_type": "LSTM",
            "train_accuracy": round(train_acc, 4),
            "test_accuracy": round(test_acc, 4),
            "samples_train": len(X_train),
            "samples_test": len(X_test),
            "sequence_length": LSTM_SEQUENCE_LENGTH,
            "hidden_size": LSTM_HIDDEN_SIZE,
            "num_layers": LSTM_NUM_LAYERS,
            "epochs": LSTM_EPOCHS,
        }

    def predict(self, df: pd.DataFrame) -> dict:
        """Predict signal using the trained LSTM model."""
        if not self.is_trained or self.model is None:
            return {"error": "LSTM model not trained yet"}

        if not _TORCH_AVAILABLE:
            return {"error": "PyTorch not installed"}

        featured = compute_features(df)
        featured = featured.dropna(subset=FEATURE_COLS)

        if len(featured) < LSTM_SEQUENCE_LENGTH:
            return {"error": f"Need at least {LSTM_SEQUENCE_LENGTH} data points"}

        # Prepare sequence
        X_raw = featured[FEATURE_COLS].values[-LSTM_SEQUENCE_LENGTH:]
        X_scaled = self.scaler.transform(X_raw)
        X_tensor = torch.FloatTensor(X_scaled).unsqueeze(0)

        self.model.eval()
        with torch.no_grad():
            output = self.model(X_tensor)
            proba = torch.softmax(output, dim=1).numpy()[0]
            pred = output.argmax(dim=1).item()

        confidence = float(max(proba))
        if confidence < HOLD_CONFIDENCE_THRESHOLD:
            signal = "HOLD"
        elif pred == 1:
            signal = "BUY"
        else:
            signal = "SELL"

        return {
            "signal": signal,
            "confidence": round(confidence, 4),
            "direction_prob": {"up": round(float(proba[1]), 4), "down": round(float(proba[0]), 4)},
            "model_type": "LSTM",
            "model_accuracy": self.accuracy,
        }

    def save_model(self, symbol: str, model_dir: str = 'models/') -> dict:
        """Persist the trained LSTM model, scaler, and metadata to disk."""
        if not self.is_trained or self.model is None:
            return {"error": "LSTM model not trained yet"}

        os.makedirs(model_dir, exist_ok=True)
        model_path = os.path.join(model_dir, f"{symbol}_lstm.pt")
        scaler_path = os.path.join(model_dir, f"{symbol}_lstm_scaler.joblib")

        torch.save({
            "state_dict": self.model.state_dict(),
            "input_size": self.model.lstm.input_size,
            "hidden_size": self.model.lstm.hidden_size,
            "num_layers": self.model.lstm.num_layers,
            "accuracy": self.accuracy,
            "trained_date": self.trained_date,
        }, model_path)
        joblib.dump(self.scaler, scaler_path)

        return {"status": "saved", "model_path": model_path, "scaler_path": scaler_path, "symbol": symbol}

    def load_model(self, symbol: str, model_dir: str = 'models/') -> dict:
        """Load a previously saved LSTM model from disk."""
        if not _TORCH_AVAILABLE:
            return {"error": "PyTorch not installed"}

        model_path = os.path.join(model_dir, f"{symbol}_lstm.pt")
        scaler_path = os.path.join(model_dir, f"{symbol}_lstm_scaler.joblib")

        if not os.path.exists(model_path) or not os.path.exists(scaler_path):
            return {"error": f"No saved LSTM model found for {symbol}"}

        checkpoint = torch.load(model_path, weights_only=False)
        net = LSTMNet(
            input_size=checkpoint["input_size"],
            hidden_size=checkpoint["hidden_size"],
            num_layers=checkpoint["num_layers"],
        )
        net.load_state_dict(checkpoint["state_dict"])
        net.eval()

        self.model = net
        self.scaler = joblib.load(scaler_path)
        self.accuracy = checkpoint["accuracy"]
        self.trained_date = checkpoint.get("trained_date")
        self.is_trained = True

        return {"status": "loaded", "symbol": symbol, "accuracy": self.accuracy}
