"""Tests for ingest.binance_vision — downloading and parsing Binance Vision ZIPs."""
import io
import zipfile
from pathlib import Path

import pandas as pd
import pytest

from ingest.binance_vision import (
    parse_klines_csv,
    parse_funding_csv,
    build_klines_url,
    build_funding_url,
)

FIXTURE_DIR = Path(__file__).resolve().parent / "fixtures"


def _wrap_in_zip(csv_path: Path) -> bytes:
    """Wrap a CSV file in a ZIP exactly as Binance Vision ships them."""
    buf = io.BytesIO()
    with zipfile.ZipFile(buf, "w") as zf:
        zf.writestr(csv_path.name.replace(".csv", ".csv"), csv_path.read_bytes())
    return buf.getvalue()


def test_parse_klines_csv_basic():
    """parse_klines_csv on a trimmed Binance CSV must return the canonical 8 columns."""
    csv_path = FIXTURE_DIR / "BTCUSDT-15m-2024-01.csv"
    zip_bytes = _wrap_in_zip(csv_path)

    df = parse_klines_csv(zip_bytes, symbol="BTCUSDT", timeframe="15m")

    assert list(df.columns) == [
        "time", "symbol", "timeframe", "open", "high", "low", "close", "volume"
    ]
    assert len(df) == 10
    assert df["symbol"].unique().tolist() == ["BTCUSDT"]
    assert df["timeframe"].unique().tolist() == ["15m"]
    # First timestamp must be 2024-01-01 00:00 UTC
    assert str(df["time"].iloc[0]) == "2024-01-01 00:00:00+00:00"
    # OHLC dtypes must be float
    for col in ("open", "high", "low", "close", "volume"):
        assert df[col].dtype == float


def test_parse_klines_csv_rejects_wrong_column_count():
    """A malformed CSV must raise, not silently truncate."""
    bad_csv = b"1,2,3,4\n5,6,7,8\n"
    buf = io.BytesIO()
    with zipfile.ZipFile(buf, "w") as zf:
        zf.writestr("bad.csv", bad_csv)

    with pytest.raises(ValueError, match="expected 12 columns"):
        parse_klines_csv(buf.getvalue(), symbol="BTCUSDT", timeframe="15m")


def test_parse_klines_csv_rejects_nan_in_ohlcv():
    """OHLCV columns with NaN must raise — silent data quality fail is worse than loud."""
    bad_csv = (
        "1704067200000,42280.50,42350.00,,42295.80,120.543,"
        "1704068099999,5099123.45,1543,60.221,2548912.11,0\n"
    )
    buf = io.BytesIO()
    with zipfile.ZipFile(buf, "w") as zf:
        zf.writestr("bad.csv", bad_csv.encode())

    with pytest.raises(ValueError, match="NaN in OHLCV"):
        parse_klines_csv(buf.getvalue(), symbol="BTCUSDT", timeframe="15m")


def test_parse_klines_csv_requires_monotonic_timestamps():
    """Out-of-order rows must raise."""
    rows = [
        "1704068100000,100,101,99,100,10,1704068999999,1000,10,5,500,0",
        "1704067200000,100,101,99,100,10,1704068099999,1000,10,5,500,0",  # earlier
    ]
    bad_csv = "\n".join(rows).encode()
    buf = io.BytesIO()
    with zipfile.ZipFile(buf, "w") as zf:
        zf.writestr("bad.csv", bad_csv)

    with pytest.raises(ValueError, match="non-monotonic"):
        parse_klines_csv(buf.getvalue(), symbol="BTCUSDT", timeframe="15m")


def test_build_klines_url():
    url = build_klines_url("BTCUSDT", "15m", 2024, 1)
    assert url == (
        "https://data.binance.vision/data/futures/um/monthly/klines/"
        "BTCUSDT/15m/BTCUSDT-15m-2024-01.zip"
    )


def test_build_funding_url():
    url = build_funding_url("BTCUSDT", 2024, 1)
    assert url == (
        "https://data.binance.vision/data/futures/um/monthly/fundingRate/"
        "BTCUSDT/BTCUSDT-fundingRate-2024-01.zip"
    )


def test_parse_funding_csv_basic():
    """parse_funding_csv on a trimmed Binance funding ZIP must return canonical 4 cols."""
    csv_path = FIXTURE_DIR / "BTCUSDT-fundingRate-2024-01.csv"
    zip_bytes = _wrap_in_zip(csv_path)

    df = parse_funding_csv(zip_bytes, symbol="BTCUSDT")

    assert list(df.columns) == ["time", "symbol", "funding_rate", "mark_price"]
    assert len(df) == 4
    assert df["symbol"].unique().tolist() == ["BTCUSDT"]
    assert str(df["time"].iloc[0]) == "2024-01-01 00:00:00+00:00"
    assert df["funding_rate"].iloc[0] == pytest.approx(0.0001)
    # mark_price is NA — Binance funding dumps don't include it
    assert pd.isna(df["mark_price"].iloc[0])


def test_parse_funding_csv_rejects_duplicate_timestamps():
    """Duplicate calc_time values must raise."""
    rows = [
        "1704067200000,8,0.0001",
        "1704067200000,8,0.0002",  # same timestamp
    ]
    bad_csv = "\n".join(rows).encode()
    buf = io.BytesIO()
    with zipfile.ZipFile(buf, "w") as zf:
        zf.writestr("bad.csv", bad_csv)

    with pytest.raises(ValueError, match="duplicate"):
        parse_funding_csv(buf.getvalue(), symbol="BTCUSDT")


def test_parse_klines_csv_rejects_duplicate_timestamps():
    """Duplicate open_time values must raise even if monotonic holds trivially."""
    rows = [
        "1704067200000,100,101,99,100,10,1704068099999,1000,10,5,500,0",
        "1704067200000,100,101,99,100,10,1704068099999,1000,10,5,500,0",
    ]
    bad_csv = "\n".join(rows).encode()
    buf = io.BytesIO()
    with zipfile.ZipFile(buf, "w") as zf:
        zf.writestr("bad.csv", bad_csv)

    with pytest.raises(ValueError, match="duplicate"):
        parse_klines_csv(buf.getvalue(), symbol="BTCUSDT", timeframe="15m")
