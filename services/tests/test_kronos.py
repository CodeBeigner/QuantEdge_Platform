"""Tests for Kronos predictor wrapper."""
import pandas as pd
import pytest
from services.kronos.config import KronosConfig, get_config
from services.kronos.predictor import KronosPredictor


@pytest.fixture
def ohlcv_df():
    dates = pd.date_range("2024-01-01", periods=100, freq="1h")
    return pd.DataFrame({
        "open": [100 + i * 0.1 for i in range(100)],
        "high": [101 + i * 0.1 for i in range(100)],
        "low": [99 + i * 0.1 for i in range(100)],
        "close": [100.5 + i * 0.1 for i in range(100)],
        "volume": [1000 + i * 10 for i in range(100)],
    }, index=dates)


class TestKronosConfig:
    def test_default_model_is_small(self):
        config = KronosConfig()
        assert config.model_size == "small"
        assert config.model_name == "NeoQuasar/Kronos-small"
        assert config.tokenizer_name == "NeoQuasar/Kronos-Tokenizer-base"

    def test_max_context_512(self):
        config = KronosConfig()
        assert config.max_context == 512

    def test_default_pred_len(self):
        config = KronosConfig()
        assert config.default_pred_len == 24


class TestKronosPredictor:
    def test_initial_state_unloaded(self):
        p = KronosPredictor()
        status = p.status
        assert "loaded" in status
        assert "model_size" in status
        if not p.is_loaded:
            assert "error" in status

    def test_forecast_unloaded_returns_error(self, ohlcv_df):
        p = KronosPredictor()
        if not p.is_loaded:
            result = p.forecast(ohlcv_df)
            assert result["status"] == "error"

    def test_status_returns_device(self):
        p = KronosPredictor()
        status = p.status
        assert status["device"] in ("cpu", "mps", "cuda")

    def test_missing_columns_raises(self, ohlcv_df):
        p = KronosPredictor()
        bad_df = pd.DataFrame({"bad_col": [1, 2, 3]})
        if p.is_loaded:
            result = p.forecast(bad_df)
            assert result["status"] == "error"
