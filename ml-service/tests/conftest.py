"""Shared pytest fixtures for ml-service tests."""
import sys
from pathlib import Path

# Allow tests to import ml-service top-level modules (feature_engine, ingest, ...)
ML_SERVICE_ROOT = Path(__file__).resolve().parent.parent
if str(ML_SERVICE_ROOT) not in sys.path:
    sys.path.insert(0, str(ML_SERVICE_ROOT))
