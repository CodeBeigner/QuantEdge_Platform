"""Tests for Latency Profiler."""
import pytest
from services.latency.profiler import (
    measure_rtt,
    check_thresholds,
    LatencyReading,
    THRESHOLDS,
    DEFAULT_ENDPOINTS,
)


class TestMeasureRTT:
    def test_measure_invalid_endpoint(self):
        rtt, status, error = measure_rtt("http://localhost:99999/nonexistent", timeout_seconds=1.0)
        assert rtt >= 0
        assert status == 0
        assert error

    def test_measure_returns_float_rtt(self):
        rtt, status, error = measure_rtt("http://localhost:99999/nonexistent", timeout_seconds=1.0)
        assert isinstance(rtt, float)


class TestCheckThresholds:
    def test_no_warnings_when_rtt_low(self):
        readings = [
            LatencyReading(broker="test", endpoint="http://test", rtt_ms=0.5, status_code=200),
        ]
        broker_map = {"test": "EQUITY"}
        warnings = check_thresholds(readings, broker_map)
        assert len(warnings) == 0

    def test_warning_when_rtt_exceeds_threshold(self):
        readings = [
            LatencyReading(broker="test", endpoint="http://test", rtt_ms=3.0, status_code=200),
        ]
        broker_map = {"test": "FUTURES"}
        warnings = check_thresholds(readings, broker_map)
        assert len(warnings) == 1
        assert warnings[0]["broker"] == "test"

    def test_no_warning_on_error(self):
        readings = [
            LatencyReading(broker="test", endpoint="http://test", rtt_ms=500.0, error="timeout"),
        ]
        broker_map = {"test": "FUTURES"}
        warnings = check_thresholds(readings, broker_map)
        assert len(warnings) == 0


class TestThresholds:
    def test_futures_threshold(self):
        assert THRESHOLDS["FUTURES"].max_rtt_ms == 1.0

    def test_forex_threshold(self):
        assert THRESHOLDS["FOREX"].max_rtt_ms == 5.0

    def test_equity_threshold(self):
        assert THRESHOLDS["EQUITY"].max_rtt_ms == 100.0


class TestDefaultEndpoints:
    def test_has_alpaca(self):
        assert "alpaca" in DEFAULT_ENDPOINTS

    def test_has_binance(self):
        assert "binance" in DEFAULT_ENDPOINTS
