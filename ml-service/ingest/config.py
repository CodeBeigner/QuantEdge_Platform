"""Configuration loaders for the ingest pipeline."""
import os


def get_database_url() -> str:
    """Return the Postgres connection URL.

    Raises:
        RuntimeError: if DATABASE_URL is not set.
    """
    url = os.environ.get("DATABASE_URL")
    if not url:
        raise RuntimeError(
            "DATABASE_URL is not set. Example: "
            "postgresql://quantedge:password@localhost:5432/quantedge"
        )
    return url
