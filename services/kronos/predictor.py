"""Kronos predictor wrapper — loads model from Hugging Face or local cache.

Installation (run manually before first use):
    git clone https://github.com/shiyu-coder/Kronos /tmp/kronos_repo
    cd /tmp/kronos_repo
    pip install -r requirements.txt
    # On Apple Silicon, ensure PyTorch MPS:
    # pip install torch torchvision torchaudio

The predictor gracefully handles missing models by returning status
indicating installation is needed.
"""
from __future__ import annotations

import logging
from pathlib import Path
from typing import Any, Dict, List, Optional

import numpy as np
import pandas as pd

from services.kronos.config import KronosConfig, get_config

_log = logging.getLogger(__name__)


class KronosPredictor:
    """Wrapper around Kronos model for probabilistic price path forecasting."""

    def __init__(self, config: Optional[KronosConfig] = None):
        self.config = config or get_config()
        self._model: Any = None
        self._tokenizer: Any = None
        self._predictor: Any = None
        self._loaded = False
        self._load_error: Optional[str] = None
        self._try_load()

    def _try_load(self) -> None:
        """Attempt to load Kronos model. Gracefully handles failure."""
        try:
            from model import Kronos as KronosModel, KronosTokenizer, KronosPredictor as KP

            tokenizer = KronosTokenizer.from_pretrained(self.config.tokenizer_name)
            model = KronosModel.from_pretrained(self.config.model_name)
            model = model.to(self.config.device)

            self._predictor = KP(model, tokenizer, max_context=self.config.max_context, device=self.config.device)
            self._tokenizer = tokenizer
            self._model = model
            self._loaded = True
            _log.info("Kronos %s loaded on %s", self.config.model_size, self.config.device)
        except ImportError:
            self._load_error = (
                "Kronos not installed. Run: git clone https://github.com/shiyu-coder/Kronos && "
                "cd Kronos && pip install -r requirements.txt"
            )
            _log.warning(self._load_error)
        except FileNotFoundError as e:
            self._load_error = f"Model weights not found: {e}"
            _log.warning(self._load_error)
        except Exception as e:
            self._load_error = f"Failed to load Kronos: {e}"
            _log.warning(self._load_error)

    @property
    def is_loaded(self) -> bool:
        return self._loaded

    @property
    def status(self) -> dict:
        return {
            "loaded": self._loaded,
            "model_size": self.config.model_size,
            "device": self.config.device,
            "max_context": self.config.max_context,
            "error": self._load_error,
        }

    def _prepare_input(
        self,
        df: pd.DataFrame,
        timestamps: Optional[pd.Series] = None,
    ) -> tuple:
        """Prepare input DataFrame for Kronos. Ensures required columns exist."""
        required_cols = ["open", "high", "low", "close"]
        for col in required_cols:
            if col not in df.columns:
                raise ValueError(f"Missing required column: {col}")

        df = df.copy()
        if "volume" not in df.columns:
            df["volume"] = 0.0
        if "amount" not in df.columns:
            df["amount"] = 0.0

        if timestamps is None:
            timestamps = pd.to_datetime(df.index)

        return df[["open", "high", "low", "close", "volume", "amount"]], timestamps

    def forecast(
        self,
        df: pd.DataFrame,
        x_timestamp: Optional[pd.Series] = None,
        y_timestamp: Optional[pd.Series] = None,
        pred_len: Optional[int] = None,
        sample_count: Optional[int] = None,
        temperature: Optional[float] = None,
        top_p: Optional[float] = None,
    ) -> dict:
        """Generate probabilistic price path forecast for a single symbol.

        Returns dict with:
            - symbol, timestamps, forecasts (list of paths for each sample),
              median_path, upper_band (90th), lower_band (10th)
        """
        if not self._loaded:
            return {"status": "error", "message": self._load_error or "Model not loaded"}

        pred_len = pred_len or self.config.default_pred_len
        sample_count = sample_count or self.config.default_sample_count
        temperature = temperature or self.config.default_temperature
        top_p = top_p or self.config.default_top_p

        try:
            x_df, x_ts = self._prepare_input(df, x_timestamp)

            if y_timestamp is None:
                if x_ts is not None and len(x_ts) > 0:
                    last_ts = x_ts.iloc[-1]
                    freq = pd.infer_freq(x_ts) if len(x_ts) > 2 else None
                    if freq is None:
                        y_timestamp = pd.date_range(start=last_ts, periods=pred_len + 1, freq="h")[1:]
                    else:
                        y_timestamp = pd.date_range(start=last_ts, periods=pred_len + 1, freq=freq)[1:]
                else:
                    y_timestamp = pd.RangeIndex(pred_len)

            pred_df = self._predictor.predict(
                df=x_df,
                x_timestamp=x_ts,
                y_timestamp=y_timestamp,
                pred_len=pred_len,
                T=temperature,
                top_p=top_p,
                sample_count=sample_count,
            )

            paths = []
            for col in ["open", "high", "low", "close"]:
                if col in pred_df.columns:
                    paths.append({
                        "field": col,
                        "values": pred_df[col].tolist(),
                    })

            timestamps = list(pred_df.index.astype(str))

            result = {
                "status": "ok",
                "timestamps": timestamps,
                "paths": paths,
                "pred_len": pred_len,
                "sample_count": sample_count,
            }

            if "close" in pred_df.columns:
                last_close = df["close"].iloc[-1] if "close" in df.columns else None
                close_path = pred_df["close"].values
                result["last_close"] = float(last_close) if last_close is not None else None
                result["forecast_return"] = float((close_path[-1] / last_close - 1) * 100) if last_close else None

            return result

        except Exception as e:
            _log.exception("Forecast failed")
            return {"status": "error", "message": str(e)}

    def forecast_batch(
        self,
        df_list: List[pd.DataFrame],
        pred_len: Optional[int] = None,
        sample_count: Optional[int] = None,
    ) -> List[dict]:
        """Generate forecasts for multiple symbols in parallel."""
        results = []
        for i, df in enumerate(df_list):
            try:
                result = self.forecast(df, pred_len=pred_len, sample_count=sample_count)
                result["index"] = i
                results.append(result)
            except Exception as e:
                results.append({"status": "error", "index": i, "message": str(e)})
        return results
