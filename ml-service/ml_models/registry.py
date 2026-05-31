"""Model versioning and on-disk registry.

Layout:
    {base_dir}/{symbol}/{model_type}/v{n}.joblib
    {base_dir}/{symbol}/{model_type}/v{n}.meta.json
    {base_dir}/{symbol}/{model_type}/latest.json  →  {"version": n}

Why a separate metadata file: joblib blobs are opaque. A sidecar JSON lets us
inspect training timestamp, out-of-sample metrics, and feature schema without
re-loading the model.

Concurrency: not thread-safe. Designed for single-process ml-service with
sequential retraining jobs.
"""
from __future__ import annotations

import json
import logging
import os
import re
import tempfile
from pathlib import Path
from typing import Any, Dict, Tuple

import joblib

_log = logging.getLogger(__name__)


def _atomic_write_text(path: Path, text: str) -> None:
    """Write text to `path` via temp-file + rename so readers never see a
    truncated file. `os.replace` is atomic on POSIX within the same filesystem."""
    path.parent.mkdir(parents=True, exist_ok=True)
    fd, tmp = tempfile.mkstemp(dir=path.parent, prefix=".tmp-", suffix=".json")
    try:
        with os.fdopen(fd, "w") as f:
            f.write(text)
        os.replace(tmp, path)
    except Exception:
        _log.exception("Failed to write %s", path)
        try:
            os.unlink(tmp)
        except FileNotFoundError:
            pass
        raise


class ModelRegistry:
    def __init__(self, base_dir: Path):
        self.base_dir = Path(base_dir)

    def _dir(self, symbol: str, model_type: str) -> Path:
        return self.base_dir / symbol / model_type

    def _next_version(self, d: Path) -> int:
        if not d.exists():
            return 1
        versions = [
            int(m.group(1))
            for p in d.iterdir()
            if (m := re.match(r"^v(\d+)\.joblib$", p.name))
        ]
        return max(versions, default=0) + 1

    def save(self, symbol: str, model_type: str, obj: Any,
             metadata: Dict[str, Any]) -> Path:
        d = self._dir(symbol, model_type)
        d.mkdir(parents=True, exist_ok=True)
        version = self._next_version(d)

        blob_path = d / f"v{version}.joblib"
        meta_path = d / f"v{version}.meta.json"
        pointer = d / "latest.json"

        joblib.dump(obj, blob_path)
        _atomic_write_text(
            meta_path,
            json.dumps({"version": version, **metadata}, default=str),
        )
        _atomic_write_text(pointer, json.dumps({"version": version}))
        return blob_path

    def load(self, symbol: str, model_type: str) -> Tuple[Any, Dict[str, Any]]:
        d = self._dir(symbol, model_type)
        pointer = d / "latest.json"
        if not pointer.exists():
            raise FileNotFoundError(f"No model registered at {d}")
        version = json.loads(pointer.read_text())["version"]
        blob = joblib.load(d / f"v{version}.joblib")
        meta = json.loads((d / f"v{version}.meta.json").read_text())
        return blob, meta
