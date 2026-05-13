"""Tests for ingest.seed_binance_vision — month iteration + orchestration."""
from datetime import date
from unittest.mock import patch
import pandas as pd

from ingest.seed_binance_vision import iter_months, plan_klines_urls, plan_funding_urls, run_klines, KlineJob


def test_iter_months_inclusive():
    got = list(iter_months(date(2024, 11, 1), date(2025, 2, 1)))
    assert got == [(2024, 11), (2024, 12), (2025, 1), (2025, 2)]


def test_iter_months_same_month():
    got = list(iter_months(date(2024, 3, 1), date(2024, 3, 1)))
    assert got == [(2024, 3)]


def test_plan_klines_urls_cartesian():
    plan = plan_klines_urls(
        symbols=["BTCUSDT"],
        timeframes=["15m", "1h"],
        start=date(2024, 1, 1),
        end=date(2024, 2, 1),
    )
    # 1 symbol x 2 TFs x 2 months = 4 URLs
    assert len(plan) == 4
    urls = [p.url for p in plan]
    assert any("BTCUSDT-15m-2024-01.zip" in u for u in urls)
    assert any("BTCUSDT-1h-2024-02.zip" in u for u in urls)


def test_plan_funding_urls_no_timeframe():
    plan = plan_funding_urls(
        symbols=["BTCUSDT", "ETHUSDT"],
        start=date(2024, 1, 1),
        end=date(2024, 2, 1),
    )
    # 2 symbols x 2 months = 4 URLs
    assert len(plan) == 4


def test_run_klines_continues_on_per_job_failure():
    """A single broken month must not abort the whole batch."""
    jobs = [
        KlineJob(symbol="BTCUSDT", timeframe="15m", year=2024, month=1,
                 url="https://example/ok.zip"),
        KlineJob(symbol="BTCUSDT", timeframe="15m", year=2024, month=2,
                 url="https://example/bad.zip"),
    ]

    good_df = pd.DataFrame({
        "time": [pd.Timestamp("2024-01-01", tz="UTC")],
        "symbol": ["BTCUSDT"], "timeframe": ["15m"],
        "open": [1.0], "high": [1.0], "low": [1.0], "close": [1.0], "volume": [1.0],
    })

    def fake_parse(data, symbol, timeframe):
        # Check if we're processing the "bad" job by inspecting the call context
        # Since we can't easily pass state through, we'll raise on second call
        if not hasattr(fake_parse, 'call_count'):
            fake_parse.call_count = 0
        fake_parse.call_count += 1

        if fake_parse.call_count == 2:
            raise ValueError("synthetic parse error")
        return good_df

    with patch("ingest.seed_binance_vision.download", return_value=b"fake-zip-bytes"), \
         patch("ingest.seed_binance_vision.parse_klines_csv", side_effect=fake_parse), \
         patch("ingest.seed_binance_vision.upsert_market_data", return_value=1), \
         patch("ingest.seed_binance_vision.connect") as mock_connect:
        mock_connect.return_value.__enter__.return_value = object()
        mock_connect.return_value.__exit__.return_value = None

        inserted, failures = run_klines(jobs)

    assert inserted == 1
    assert failures == 1
