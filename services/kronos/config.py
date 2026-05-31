"""Kronos service configuration."""
from __future__ import annotations

import os
from dataclasses import dataclass


@dataclass
class KronosConfig:
    model_size: str = os.getenv("KRONOS_MODEL_SIZE", "small")
    model_path: str = os.getenv("KRONOS_MODEL_PATH", "./models/kronos")
    device: str = os.getenv("KRONOS_DEVICE", "cpu")
    max_context: int = int(os.getenv("KRONOS_MAX_CONTEXT", "512"))
    default_pred_len: int = int(os.getenv("KRONOS_PRED_LEN", "24"))
    default_sample_count: int = int(os.getenv("KRONOS_SAMPLE_COUNT", "5"))
    default_temperature: float = float(os.getenv("KRONOS_TEMPERATURE", "1.0"))
    default_top_p: float = float(os.getenv("KRONOS_TOP_P", "0.9"))

    @property
    def model_name(self) -> str:
        return f"NeoQuasar/Kronos-{self.model_size}"

    @property
    def tokenizer_name(self) -> str:
        if self.model_size in ("small", "base"):
            return "NeoQuasar/Kronos-Tokenizer-base"
        return "NeoQuasar/Kronos-Tokenizer-2k"


def get_config() -> KronosConfig:
    return KronosConfig()
