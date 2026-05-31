"""Shared test fixtures for Pillar C tests."""
import tempfile

import pytest

from services.risk.config import (
    Direction,
    PortfolioState,
    PredictionSignal,
    RiskConfig,
)
from services.execution.base import Position


@pytest.fixture
def risk_config() -> RiskConfig:
    return RiskConfig(
        min_confidence_threshold=0.55,
        kelly_fraction=0.25,
        max_position_pct=0.15,
        max_total_exposure=3.0,
        max_drawdown=0.08,
        daily_loss_limit=5000.0,
        daily_var_limit=10000.0,
        slippage_threshold=0.02,
    )


@pytest.fixture
def bull_signal() -> PredictionSignal:
    return PredictionSignal(
        asset="AAPL",
        direction=Direction.LONG,
        probability=0.65,
        confidence=0.72,
        entry_price=150.0,
        stop_loss=145.0,
        take_profit=160.0,
        rationale="Kronos forecast bullish, fundamental DCF supports upside",
    )


@pytest.fixture
def bear_signal() -> PredictionSignal:
    return PredictionSignal(
        asset="TSLA",
        direction=Direction.SHORT,
        probability=0.60,
        confidence=0.68,
        entry_price=250.0,
        stop_loss=260.0,
        take_profit=230.0,
        rationale="Earnings miss, guidance cut",
    )


@pytest.fixture
def weak_signal() -> PredictionSignal:
    return PredictionSignal(
        asset="META",
        direction=Direction.LONG,
        probability=0.52,
        confidence=0.51,
        entry_price=300.0,
        rationale="Uncertain macro environment",
    )


@pytest.fixture
def healthy_portfolio() -> PortfolioState:
    return PortfolioState(
        nav=100_000.0,
        cash=100_000.0,
        peak_equity=100_000.0,
        current_drawdown=0.0,
        positions=[],
        daily_pnl=0.0,
        total_exposure=0.0,
    )


@pytest.fixture
def leveraged_portfolio() -> PortfolioState:
    return PortfolioState(
        nav=100_000.0,
        cash=20_000.0,
        peak_equity=120_000.0,
        current_drawdown=0.05,
        positions=[
            Position(asset="NVDA", size=100, entry_price=800.0, current_price=850.0),
            Position(asset="MSFT", size=200, entry_price=400.0, current_price=410.0),
        ],
        daily_pnl=-2000.0,
        total_exposure=160_000.0,
    )


@pytest.fixture
def drawn_down_portfolio() -> PortfolioState:
    return PortfolioState(
        nav=90_000.0,
        cash=90_000.0,
        peak_equity=100_000.0,
        current_drawdown=0.10,
        positions=[],
        daily_pnl=-15000.0,
        total_exposure=0.0,
    )


@pytest.fixture
def temp_flag_dir() -> str:
    with tempfile.TemporaryDirectory() as d:
        yield d
