"""One-shot seed of Binance Vision OHLCV and funding history into Postgres.

Usage:
    python -m ingest.seed_binance_vision \
        --symbols BTCUSDT,ETHUSDT \
        --timeframes 15m,1h,4h \
        --start 2020-01 \
        --end 2026-05 \
        --types klines,fundingRate

Requires DATABASE_URL env var. Idempotent — safe to re-run.
"""
from __future__ import annotations

import argparse
import logging
import sys
from dataclasses import dataclass
from datetime import date
from typing import Iterable, List

from .binance_vision import (
    build_funding_url,
    build_klines_url,
    download,
    parse_funding_csv,
    parse_klines_csv,
)
from .db import connect, upsert_funding_rate, upsert_market_data

log = logging.getLogger("ingest.seed")


@dataclass(frozen=True)
class KlineJob:
    symbol: str
    timeframe: str
    year: int
    month: int
    url: str


@dataclass(frozen=True)
class FundingJob:
    symbol: str
    year: int
    month: int
    url: str


def iter_months(start: date, end: date) -> Iterable[tuple[int, int]]:
    """Yield (year, month) tuples from start to end inclusive, ignoring the day."""
    if start > end:
        raise ValueError(f"start {start} > end {end}")
    y, m = start.year, start.month
    while (y, m) <= (end.year, end.month):
        yield y, m
        m += 1
        if m > 12:
            m = 1
            y += 1


def plan_klines_urls(symbols: List[str], timeframes: List[str],
                     start: date, end: date) -> List[KlineJob]:
    jobs: List[KlineJob] = []
    for symbol in symbols:
        for tf in timeframes:
            for y, m in iter_months(start, end):
                jobs.append(KlineJob(
                    symbol=symbol, timeframe=tf, year=y, month=m,
                    url=build_klines_url(symbol, tf, y, m),
                ))
    return jobs


def plan_funding_urls(symbols: List[str], start: date, end: date) -> List[FundingJob]:
    jobs: List[FundingJob] = []
    for symbol in symbols:
        for y, m in iter_months(start, end):
            jobs.append(FundingJob(
                symbol=symbol, year=y, month=m,
                url=build_funding_url(symbol, y, m),
            ))
    return jobs


def run_klines(jobs: List[KlineJob]) -> int:
    total_inserted = 0
    with connect() as conn:
        for job in jobs:
            data = download(job.url)
            if data is None:
                log.info("skip (404): %s %s %d-%02d", job.symbol, job.timeframe, job.year, job.month)
                continue
            df = parse_klines_csv(data, job.symbol, job.timeframe)
            inserted = upsert_market_data(conn, df)
            total_inserted += inserted
            log.info("inserted %d rows for %s %s %d-%02d (have %d)",
                     inserted, job.symbol, job.timeframe, job.year, job.month, len(df))
    return total_inserted


def run_funding(jobs: List[FundingJob]) -> int:
    total_inserted = 0
    with connect() as conn:
        for job in jobs:
            data = download(job.url)
            if data is None:
                log.info("skip (404): %s funding %d-%02d", job.symbol, job.year, job.month)
                continue
            df = parse_funding_csv(data, job.symbol)
            inserted = upsert_funding_rate(conn, df)
            total_inserted += inserted
            log.info("inserted %d rows for %s funding %d-%02d (have %d)",
                     inserted, job.symbol, job.year, job.month, len(df))
    return total_inserted


def _parse_month(arg: str) -> date:
    y, m = arg.split("-")
    return date(int(y), int(m), 1)


def main(argv: List[str] | None = None) -> int:
    p = argparse.ArgumentParser(description="Seed Binance Vision data into Postgres")
    p.add_argument("--symbols", required=True, help="Comma-separated, e.g. BTCUSDT,ETHUSDT")
    p.add_argument("--timeframes", default="15m,1h,4h",
                   help="Comma-separated timeframes for klines")
    p.add_argument("--start", required=True, type=_parse_month, help="YYYY-MM")
    p.add_argument("--end", required=True, type=_parse_month, help="YYYY-MM")
    p.add_argument("--types", default="klines,fundingRate",
                   help="Comma-separated subset of {klines, fundingRate}")
    p.add_argument("--dry-run", action="store_true", help="Print planned URLs, do not download")
    args = p.parse_args(argv)

    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s %(levelname)s %(name)s %(message)s",
    )

    symbols = [s.strip() for s in args.symbols.split(",") if s.strip()]
    timeframes = [t.strip() for t in args.timeframes.split(",") if t.strip()]
    types = {t.strip() for t in args.types.split(",") if t.strip()}

    klines_jobs = plan_klines_urls(symbols, timeframes, args.start, args.end) \
        if "klines" in types else []
    funding_jobs = plan_funding_urls(symbols, args.start, args.end) \
        if "fundingRate" in types else []

    log.info("Planned %d klines jobs, %d funding jobs",
             len(klines_jobs), len(funding_jobs))

    if args.dry_run:
        for j in klines_jobs:
            print(j.url)
        for j in funding_jobs:
            print(j.url)
        return 0

    klines_inserted = run_klines(klines_jobs) if klines_jobs else 0
    funding_inserted = run_funding(funding_jobs) if funding_jobs else 0

    log.info("Done. Inserted %d klines rows, %d funding rows.",
             klines_inserted, funding_inserted)
    return 0


if __name__ == "__main__":
    sys.exit(main())
