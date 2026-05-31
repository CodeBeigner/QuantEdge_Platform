"""Latency Profiler — FastAPI service."""
from __future__ import annotations

import logging
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from services.latency.profiler import latency_report, profile_brokers, check_thresholds

_log = logging.getLogger(__name__)

app = FastAPI(title="QuantEdge Latency Profiler", version="1.0.0")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["http://localhost:3000"],
    allow_methods=["*"],
    allow_headers=["*"],
)


@app.get("/health")
async def health():
    return {"status": "UP", "service": "latency-profiler"}


@app.get("/infrastructure/latency-report")
async def get_latency_report():
    return latency_report()
