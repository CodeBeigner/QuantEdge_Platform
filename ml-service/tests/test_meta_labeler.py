"""Tests for ml_models.meta_labeler — triple-barrier meta-filter over primary signals."""
import numpy as np
import pandas as pd
import pytest

from ml_models.meta_labeler import MetaLabeler


@pytest.fixture
def toy_training_frame():
    """Feature rows with a clean signal: higher `momentum` → higher P(TP).

    The meta-labeler should learn this monotone relationship.
    """
    rng = np.random.default_rng(0)
    n = 400
    momentum = rng.normal(0, 1, n)
    noise = rng.normal(0, 0.5, n)
    # Labels: TP (1) when momentum + noise > 0, else SL (0)
    labels = ((momentum + noise) > 0).astype(int)
    return pd.DataFrame({
        "momentum": momentum,
        "volatility": rng.normal(0, 1, n),
        "direction": rng.choice([-1, 1], size=n),
        "label": labels,
    })


def test_train_returns_reasonable_accuracy(toy_training_frame):
    model = MetaLabeler(feature_cols=["momentum", "volatility", "direction"])
    result = model.train(toy_training_frame, label_col="label")
    # Toy problem is learnable; expect > 0.65 train accuracy
    assert result["train_accuracy"] > 0.65


def test_predict_returns_probability_in_zero_one(toy_training_frame):
    model = MetaLabeler(feature_cols=["momentum", "volatility", "direction"])
    model.train(toy_training_frame, label_col="label")
    row = toy_training_frame.iloc[[0]].drop(columns=["label"])
    out = model.predict(row)
    assert 0.0 <= out["meta_prob"] <= 1.0
    assert "direction" in out


def test_predict_before_training_raises(toy_training_frame):
    model = MetaLabeler(feature_cols=["momentum", "volatility", "direction"])
    with pytest.raises(RuntimeError, match="not trained"):
        model.predict(toy_training_frame.iloc[[0]])


def test_train_rejects_timeout_labels(toy_training_frame):
    """-1 labels (timeouts) must be filtered out of training, not fed to XGBoost."""
    frame = toy_training_frame.copy()
    frame.loc[:50, "label"] = -1
    model = MetaLabeler(feature_cols=["momentum", "volatility", "direction"])
    result = model.train(frame, label_col="label")
    # n_train should be full length minus the 51 timeout rows
    assert result["n_train"] == len(frame) - 51
