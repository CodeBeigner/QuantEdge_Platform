"""
XGBoost + LSTM Signal Models — Predicts next-day return direction.

- SignalModel: XGBoost classifier on technical indicators
- LSTMSignalModel: LSTM neural network on price sequences
"""
import numpy as np
import pandas as pd
from xgboost import XGBClassifier
from sklearn.model_selection import train_test_split
from sklearn.metrics import accuracy_score
from feature_engine import compute_features, FEATURE_COLS
from typing import Optional

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

    def train(self, df: pd.DataFrame) -> dict:
        """Train the LSTM model on OHLCV data.

        Returns training metrics.
        """
        try:
            import torch
            import torch.nn as nn
            from sklearn.preprocessing import StandardScaler
        except ImportError:
            return {"error": "PyTorch not installed. Run: pip install torch"}

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

        class LSTMNet(nn.Module):
            def __init__(self):
                super().__init__()
                self.lstm = nn.LSTM(
                    input_size=input_size,
                    hidden_size=LSTM_HIDDEN_SIZE,
                    num_layers=LSTM_NUM_LAYERS,
                    batch_first=True,
                    dropout=0.2,
                )
                self.fc = nn.Linear(LSTM_HIDDEN_SIZE, 2)

            def forward(self, x):
                lstm_out, _ = self.lstm(x)
                return self.fc(lstm_out[:, -1, :])

        model = LSTMNet()
        criterion = nn.CrossEntropyLoss()
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

        try:
            import torch
        except ImportError:
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
