"""Tests for ingest.seed_binance_vision — month iteration + orchestration."""
from datetime import date

from ingest.seed_binance_vision import iter_months, plan_klines_urls, plan_funding_urls


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
