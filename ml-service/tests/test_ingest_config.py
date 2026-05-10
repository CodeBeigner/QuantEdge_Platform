"""Tests for ingest.config — DB URL resolution."""
import os

import pytest

from ingest.config import get_database_url


def test_returns_env_value(monkeypatch):
    monkeypatch.setenv("DATABASE_URL", "postgresql://u:p@host:5432/db")
    assert get_database_url() == "postgresql://u:p@host:5432/db"


def test_raises_when_env_missing(monkeypatch):
    monkeypatch.delenv("DATABASE_URL", raising=False)
    with pytest.raises(RuntimeError, match="DATABASE_URL"):
        get_database_url()
