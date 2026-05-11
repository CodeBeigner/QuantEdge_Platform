"""Tests for ml_models.registry — model versioning + latest pointer."""
import json
from pathlib import Path

import pytest

from ml_models.registry import ModelRegistry


def test_save_and_load_roundtrip(tmp_path):
    reg = ModelRegistry(base_dir=tmp_path)
    obj = {"weights": [0.1, 0.2, 0.3]}
    meta = {"trained_at": "2024-01-01", "oos_ic": 0.04}

    path = reg.save("BTCUSDT", "meta", obj, metadata=meta)
    assert path.exists()

    loaded_obj, loaded_meta = reg.load("BTCUSDT", "meta")
    assert loaded_obj == obj
    assert loaded_meta["trained_at"] == "2024-01-01"


def test_save_increments_version(tmp_path):
    reg = ModelRegistry(base_dir=tmp_path)
    v1 = reg.save("BTCUSDT", "meta", {"w": 1}, metadata={})
    v2 = reg.save("BTCUSDT", "meta", {"w": 2}, metadata={})
    assert "v1" in v1.name
    assert "v2" in v2.name


def test_latest_pointer_is_updated(tmp_path):
    reg = ModelRegistry(base_dir=tmp_path)
    reg.save("BTCUSDT", "meta", {"w": 1}, metadata={})
    reg.save("BTCUSDT", "meta", {"w": 2}, metadata={})

    pointer = tmp_path / "BTCUSDT" / "meta" / "latest.json"
    data = json.loads(pointer.read_text())
    assert data["version"] == 2


def test_load_missing_returns_none_tuple(tmp_path):
    reg = ModelRegistry(base_dir=tmp_path)
    with pytest.raises(FileNotFoundError):
        reg.load("NOSYM", "meta")
