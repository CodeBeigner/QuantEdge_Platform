"""Latency measurement — broker/exchange health endpoint RTT via stdlib urllib."""
from __future__ import annotations

import logging
import time
import urllib.request
from dataclasses import dataclass, field
from datetime import datetime
from typing import Dict, List, Optional

_log = logging.getLogger(__name__)


@dataclass
class LatencyReading:
    broker: str
    endpoint: str
    rtt_ms: float
    status_code: int = 0
    error: str = ""
    timestamp: str = field(default_factory=lambda: datetime.utcnow().isoformat())


@dataclass
class LatencyThreshold:
    asset_class: str
    max_rtt_ms: float
    label: str


THRESHOLDS = {
    "FUTURES": LatencyThreshold("FUTURES", 1.0, "Warning: RTT > 1ms — co-location recommended for futures"),
    "FOREX": LatencyThreshold("FOREX", 5.0, "Warning: RTT > 5ms — low-latency VPS recommended for forex"),
    "CRYPTO": LatencyThreshold("CRYPTO", 20.0, "Warning: RTT > 20ms"),
    "EQUITY": LatencyThreshold("EQUITY", 100.0, "Cloud acceptable for equities"),
}

DEFAULT_ENDPOINTS = {
    "alpaca": "https://api.alpaca.markets/v2/clock",
    "delta_exchange": "https://api.india.delta.exchange/v2/assets",
    "binance": "https://api.binance.com/api/v3/ping",
}


def measure_rtt(endpoint: str, timeout_seconds: float = 5.0) -> tuple:
    """Measure round-trip time to an HTTP endpoint. Returns (rtt_ms, status_code, error)."""
    start = time.perf_counter()
    try:
        req = urllib.request.Request(endpoint, method="GET")
        with urllib.request.urlopen(req, timeout=timeout_seconds) as resp:
            rtt_ms = (time.perf_counter() - start) * 1000
            return round(rtt_ms, 2), resp.status, ""
    except Exception as e:
        rtt_ms = (time.perf_counter() - start) * 1000
        return round(rtt_ms, 2), 0, str(e)


def profile_brokers(
    endpoints: Optional[Dict[str, str]] = None,
    timeout_seconds: float = 5.0,
) -> List[LatencyReading]:
    """Measure RTT for all configured broker endpoints."""
    targets = endpoints or DEFAULT_ENDPOINTS
    readings = []

    for broker, endpoint in targets.items():
        rtt, status, error = measure_rtt(endpoint, timeout_seconds)
        readings.append(LatencyReading(
            broker=broker,
            endpoint=endpoint,
            rtt_ms=rtt,
            status_code=status,
            error=error,
        ))

    return readings


def check_thresholds(
    readings: List[LatencyReading],
    broker_asset_map: Optional[Dict[str, str]] = None,
) -> List[dict]:
    """Check latency readings against asset-class thresholds. Returns warnings list."""
    warnings = []
    default_map = {"alpaca": "EQUITY", "delta_exchange": "CRYPTO", "binance": "CRYPTO"}
    asset_map = broker_asset_map or default_map

    for reading in readings:
        asset_class = asset_map.get(reading.broker, "EQUITY")
        threshold = THRESHOLDS.get(asset_class)
        if threshold and reading.rtt_ms > threshold.max_rtt_ms and not reading.error:
            warnings.append({
                "broker": reading.broker,
                "asset_class": asset_class,
                "rtt_ms": reading.rtt_ms,
                "threshold_ms": threshold.max_rtt_ms,
                "message": threshold.label,
                "exceeded_by_ms": round(reading.rtt_ms - threshold.max_rtt_ms, 2),
            })

    return warnings


def latency_report() -> dict:
    """Generate full latency report with readings and warnings."""
    readings = profile_brokers()
    warnings = check_thresholds(readings)

    return {
        "timestamp": datetime.utcnow().isoformat(),
        "readings": [
            {
                "broker": r.broker,
                "endpoint": r.endpoint,
                "rtt_ms": r.rtt_ms,
                "status_code": r.status_code,
                "error": r.error,
            }
            for r in readings
        ],
        "warnings": warnings,
        "warning_count": len(warnings),
        "brokers_profiled": len(readings),
    }
