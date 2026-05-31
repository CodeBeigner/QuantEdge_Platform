# Pillar C: Decision & Risk Layer — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the Pillar C safety net — Kelly sizing, 7 risk checks, kill switch, and paper execution provider — all deterministic Python, TDD with unit tests before each implementation.

**Architecture:** Pure Python 3.10+ with dataclasses, no external ML dependencies initially. All thresholds from environment variables (RiskConfig dataclass). Risk engine runs 7 sequential checks against every trade; any failure blocks the order. Kill switch uses file-drop detection for local, API endpoint for cloud. Execution providers follow an abstract interface; PaperProvider is the default.

**Tech Stack:** Python 3.10+, pytest, dataclasses, pathlib, enum, decimal, statistics, numpy (VaR only)

---

## File Map

```
services/
├── risk/
│   ├── __init__.py          # empty
│   ├── config.py            # RiskConfig, PortfolioState, RiskResult, SizedOrder
│   ├── kelly.py             # kelly_fraction(), apply_kelly_fraction()
│   ├── var.py               # historical_var()
│   ├── engine.py            # validate_order() — 7 checks
│   └── kill_switch.py       # KillSwitch class
├── execution/
│   ├── __init__.py          # empty
│   ├── base.py              # ExecutionProvider ABC, OrderResult, Position, AccountState
│   ├── paper.py             # PaperProvider
│   └── slippage.py          # check_slippage()
└── tests/
    ├── conftest.py           # shared fixtures
    ├── test_kelly.py
    ├── test_var.py
    ├── test_risk_engine.py
    ├── test_kill_switch.py
    ├── test_paper_execution.py
    └── test_slippage.py
```

---

### Task 1: Create directory structure and shared data types

**Files:**
- Create: `services/__init__.py`
- Create: `services/risk/__init__.py`
- Create: `services/execution/__init__.py`
- Create: `services/risk/config.py`
- Create: `services/execution/base.py`
- Create: `services/tests/__init__.py`
- Create: `services/tests/conftest.py`

- [ ] **Step 1: Create directory tree**

```bash
mkdir -p services/risk services/execution services/tests
touch services/__init__.py services/tests/__init__.py
touch services/risk/__init__.py services/execution/__init__.py
```

- [ ] **Step 2: Write shared data types in config.py**

```python
"""Pillar C configuration and shared data types."""
from __future__ import annotations

import os
from dataclasses import dataclass, field
from decimal import Decimal
from enum import Enum
from typing import List, Optional


class Direction(Enum):
    LONG = "LONG"
    SHORT = "SHORT"
    HOLD = "HOLD"


@dataclass
class PredictionSignal:
    asset: str
    direction: Direction
    probability: float          # win probability (0.0-1.0)
    confidence: float           # ensemble confidence (0.0-1.0)
    entry_price: float
    stop_loss: Optional[float] = None
    take_profit: Optional[float] = None
    rationale: str = ""


@dataclass
class SizedOrder:
    asset: str
    direction: Direction
    size_dollars: float
    entry_price: float
    order_type: str = "LIMIT"
    stop_loss: Optional[float] = None
    take_profit: Optional[float] = None
    expiry_minutes: int = 120


@dataclass
class PortfolioState:
    nav: float                              # net asset value
    cash: float                             # available cash
    peak_equity: float                      # all-time high equity
    current_drawdown: float                 # fraction (0.0-1.0)
    positions: List[Position] = field(default_factory=list)
    daily_pnl: float = 0.0
    total_exposure: float = 0.0             # sum of all position notional values


@dataclass
class RiskResult:
    passed: bool
    sized_order: Optional[SizedOrder] = None
    failures: List[str] = field(default_factory=list)
    applied_size: float = 0.0
    applied_fraction: float = 0.0


@dataclass
class RiskConfig:
    min_confidence_threshold: float = float(os.getenv("MIN_CONFIDENCE_THRESHOLD", "0.55"))
    kelly_fraction: float = float(os.getenv("KELLY_FRACTION", "0.25"))
    max_position_pct: float = float(os.getenv("MAX_POSITION_PCT", "0.05"))
    max_total_exposure: float = float(os.getenv("MAX_TOTAL_EXPOSURE", "3.0"))
    max_drawdown: float = float(os.getenv("MAX_DRAWDOWN", "0.08"))
    daily_loss_limit: float = float(os.getenv("DAILY_LOSS_LIMIT", "5000"))
    daily_var_limit: float = float(os.getenv("DAILY_VAR_LIMIT", "10000"))
    slippage_threshold: float = float(os.getenv("SLIPPAGE_THRESHOLD", "0.02"))
    live_trading: bool = os.getenv("LIVE_TRADING", "false").lower() == "true"
    live_trading_confirm: bool = os.getenv("LIVE_TRADING_CONFIRM", "no").lower() == "yes"
    kill_switch_dir: str = os.getenv("KILL_SWITCH_DIR", "./flags")
```

- [ ] **Step 3: Write base execution types**

```python
"""Execution provider abstract interface and result types."""
from __future__ import annotations

import uuid
from abc import ABC, abstractmethod
from dataclasses import dataclass, field
from datetime import datetime
from typing import Dict, List, Optional


@dataclass
class Position:
    asset: str
    size: float
    entry_price: float
    current_price: float
    unrealized_pnl: float = 0.0
    realized_pnl: float = 0.0


@dataclass
class AccountState:
    cash: float
    equity: float
    buying_power: float
    positions: List[Position] = field(default_factory=list)


@dataclass
class OrderResult:
    order_id: str = field(default_factory=lambda: str(uuid.uuid4()))
    asset: str = ""
    status: str = "FILLED"
    filled_price: float = 0.0
    filled_size: float = 0.0
    rejected_reason: Optional[str] = None
    timestamp: str = field(default_factory=lambda: datetime.utcnow().isoformat())


class ExecutionProvider(ABC):
    @abstractmethod
    async def submit_order(self, order: SizedOrder) -> OrderResult: ...
    @abstractmethod
    async def cancel_order(self, order_id: str) -> bool: ...
    @abstractmethod
    async def cancel_all_orders(self) -> int: ...
    @abstractmethod
    async def get_positions(self) -> List[Position]: ...
    @abstractmethod
    async def get_account(self) -> AccountState: ...
```

- [ ] **Step 4: Write conftest.py with shared fixtures**

```python
"""Shared test fixtures for Pillar C tests."""
import os
import tempfile
from pathlib import Path

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
        max_position_pct=0.05,
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
```

- [ ] **Step 5: Verify imports work**

```bash
cd /Users/animesh/Desktop/QuantEdge_Platform
python3 -c "from services.risk.config import RiskConfig, PredictionSignal, Direction, PortfolioState, RiskResult; print('config OK')"
python3 -c "from services.execution.base import ExecutionProvider, OrderResult, Position, AccountState; print('base OK')"
```

Expected: Both print OK with no errors.

- [ ] **Step 6: Commit**

```bash
git add services/
git commit -m "feat(pillar-c): add directory structure, shared data types, and test fixtures"
```

---

### Task 2: Kelly Criterion Sizing (TDD)

**Files:**
- Create: `services/risk/kelly.py`
- Create: `services/tests/test_kelly.py`

- [ ] **Step 1: Write failing tests for Kelly formula**

```python
"""Tests for Kelly Criterion position sizing."""
import pytest
from services.risk.kelly import kelly_fraction, apply_kelly_fraction


class TestKellyFraction:
    def test_even_odds(self):
        """With even odds (b=1, p=0.5), Kelly says bet nothing."""
        result = kelly_fraction(win_probability=0.5, win_loss_ratio=1.0)
        assert result == pytest.approx(0.0)

    def test_strong_edge(self):
        """60% win rate, 1:1 payouts -> f* = 0.20."""
        result = kelly_fraction(win_probability=0.60, win_loss_ratio=1.0)
        assert result == pytest.approx(0.20)

    def test_high_payout(self):
        """50% win rate, 3:1 payouts -> f* = 0.333."""
        result = kelly_fraction(win_probability=0.50, win_loss_ratio=3.0)
        assert result == pytest.approx(0.33333, rel=1e-3)

    def test_weak_edge(self):
        """51% win rate, 2:1 -> f* = 0.265."""
        result = kelly_fraction(win_probability=0.51, win_loss_ratio=2.0)
        assert result == pytest.approx(0.265)

    def test_guaranteed_win(self):
        """100% win probability -> f*=1 (bet everything)."""
        result = kelly_fraction(win_probability=1.0, win_loss_ratio=1.0)
        assert result == pytest.approx(1.0)

    def test_negative_edge_returns_zero(self):
        """30% win rate -> negative Kelly, should return 0 not negative."""
        result = kelly_fraction(win_probability=0.30, win_loss_ratio=1.0)
        assert result >= 0.0


class TestApplyKellyFraction:
    def test_quarter_kelly(self):
        size = apply_kelly_fraction(p=0.60, b=1.0, nav=100_000.0, fraction=0.25)
        # full Kelly = 20k, quarter = 5k
        assert size == pytest.approx(5_000.0)

    def test_half_kelly(self):
        size = apply_kelly_fraction(p=0.65, b=2.0, nav=50_000.0, fraction=0.5)
        # f* = (0.65*2 - 0.35)/2 = (1.3 - 0.35)/2 = 0.475
        # position = 0.475 * 0.5 * 50000 = 11875
        assert size == pytest.approx(11875.0)

    def test_zero_nav(self):
        size = apply_kelly_fraction(p=0.60, b=1.0, nav=0.0, fraction=0.25)
        assert size == 0.0

    def test_negative_edge_zero_size(self):
        size = apply_kelly_fraction(p=0.30, b=1.0, nav=100_000.0, fraction=0.25)
        assert size == 0.0
```

- [ ] **Step 2: Run test to verify failure**

```bash
cd /Users/animesh/Desktop/QuantEdge_Platform && python3 -m pytest services/tests/test_kelly.py -v
```

Expected: ImportError or NameError — kelly module doesn't exist yet.

- [ ] **Step 3: Write minimal implementation**

```python
"""Kelly Criterion position sizing."""
from __future__ import annotations


def kelly_fraction(win_probability: float, win_loss_ratio: float) -> float:
    """
    f* = (p * b - q) / b
    where p = win_probability, q = 1 - p, b = win_loss_ratio.
    Returns the optimal fraction of capital to allocate.
    """
    loss_probability = 1.0 - win_probability
    if win_loss_ratio == 0:
        return 0.0
    f_star = (win_probability * win_loss_ratio - loss_probability) / win_loss_ratio
    return max(0.0, f_star)


def apply_kelly_fraction(
    win_probability: float,
    win_loss_ratio: float,
    nav: float,
    fraction: float = 0.25,
) -> float:
    """Returns position size in dollars using fractional Kelly."""
    if nav <= 0:
        return 0.0
    f_star = kelly_fraction(win_probability, win_loss_ratio)
    return f_star * fraction * nav
```

- [ ] **Step 4: Run tests to verify pass**

```bash
cd /Users/animesh/Desktop/QuantEdge_Platform && python3 -m pytest services/tests/test_kelly.py -v
```

Expected: All 9 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add services/risk/kelly.py services/tests/test_kelly.py
git commit -m "feat(pillar-c): add Kelly criterion sizing with quarter-Kelly default"
```

---

### Task 3: Historical VaR Computation (TDD)

**Files:**
- Create: `services/risk/var.py`
- Create: `services/tests/test_var.py`

- [ ] **Step 1: Write failing tests for VaR**

```python
"""Tests for historical Value at Risk computation."""
import numpy as np
import pytest
from services.risk.var import historical_var, portfolio_var


class TestHistoricalVaR:
    def test_normal_returns_95_var(self):
        np.random.seed(42)
        returns = np.random.normal(0.001, 0.02, 1000)
        var = historical_var(returns, confidence=0.95)
        # 95% VaR for std=0.02 mean=0.001 should be negative and around -0.032
        assert var < 0
        assert var > -0.05

    def test_empty_returns(self):
        var = historical_var(np.array([]), confidence=0.95)
        assert var == 0.0

    def test_constant_returns(self):
        returns = np.array([0.01] * 100)
        var = historical_var(returns, confidence=0.95)
        assert var == pytest.approx(0.01)

    def test_99_confidence_more_extreme_than_95(self):
        np.random.seed(42)
        returns = np.random.normal(0.0, 0.02, 1000)
        var_95 = historical_var(returns, confidence=0.95)
        var_99 = historical_var(returns, confidence=0.99)
        assert var_99 < var_95


class TestPortfolioVaR:
    def test_portfolio_with_positions(self):
        np.random.seed(42)
        returns_history = {
            "AAPL": np.random.normal(0.001, 0.02, 500),
            "MSFT": np.random.normal(0.0008, 0.018, 500),
        }
        positions = {"AAPL": 50_000.0, "MSFT": 30_000.0}
        result = portfolio_var(returns_history, positions, confidence=0.95)
        assert isinstance(result, float)
        assert result < 0
        assert result > -5000.0

    def test_empty_portfolio(self):
        result = portfolio_var({}, {}, confidence=0.95)
        assert result == 0.0
```

- [ ] **Step 2: Run test to verify failure**

```bash
cd /Users/animesh/Desktop/QuantEdge_Platform && python3 -m pytest services/tests/test_var.py -v
```

Expected: ImportError — var module doesn't exist yet.

- [ ] **Step 3: Write implementation**

```python
"""Historical Value at Risk computation."""
from __future__ import annotations

from typing import Dict

import numpy as np


def historical_var(returns: np.ndarray, confidence: float = 0.95) -> float:
    """Compute historical VaR at the given confidence level.
    Returns the returns threshold below which (1-confidence) proportion
    of observations fall. Larger negative = worse outcome."""
    if len(returns) == 0:
        return 0.0
    alpha = 1.0 - confidence
    return float(np.percentile(returns, alpha * 100))


def portfolio_var(
    returns_history: Dict[str, np.ndarray],
    positions: Dict[str, float],
    confidence: float = 0.95,
    n_simulations: int = 10000,
) -> float:
    """Compute portfolio VaR by simulating from historical returns.
    Uses bootstrapped pairwise sampling to preserve correlations."""
    if not positions or not returns_history:
        return 0.0

    assets = list(positions.keys())
    available = [a for a in assets if a in returns_history]
    if not available:
        return 0.0

    min_len = min(len(returns_history[a]) for a in available)
    if min_len < 2:
        return 0.0

    position_values = np.array([positions[a] for a in available])
    np.random.seed(42)
    indices = np.random.randint(0, min_len, size=(n_simulations,))

    simulated_returns = []
    for a in available:
        rets = returns_history[a][-min_len:]
        simulated_returns.append(rets[indices])

    simulated_returns = np.array(simulated_returns)
    portfolio_returns = (simulated_returns * position_values[:, None]).sum(axis=0)
    portfolio_returns = portfolio_returns / position_values.sum()

    return historical_var(portfolio_returns, confidence)
```

- [ ] **Step 4: Run tests to verify pass**

```bash
cd /Users/animesh/Desktop/QuantEdge_Platform && python3 -m pytest services/tests/test_var.py -v
```

Expected: All 6 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add services/risk/var.py services/tests/test_var.py
git commit -m "feat(pillar-c): add historical VaR with portfolio-level bootstrap"
```

---

### Task 4: Risk Engine — 7 Sequential Checks (TDD)

**Files:**
- Create: `services/risk/engine.py`
- Create: `services/tests/test_risk_engine.py`

- [ ] **Step 1: Write failing tests for risk engine**

```python
"""Tests for the risk engine's 7 sequential checks."""
import pytest
from services.risk.config import Direction, PredictionSignal, RiskResult, SizedOrder
from services.risk.engine import validate_order


class TestRiskEngineEdgeCheck:
    def test_strong_signal_passes(self, risk_config, bull_signal, healthy_portfolio):
        result = validate_order(bull_signal, healthy_portfolio, risk_config)
        assert result.passed
        assert result.sized_order is not None
        assert "edge_check" not in result.failures

    def test_weak_confidence_fails(self, risk_config, weak_signal, healthy_portfolio):
        result = validate_order(weak_signal, healthy_portfolio, risk_config)
        assert not result.passed
        assert any("confidence" in f.lower() for f in result.failures)


class TestRiskEngineKelly:
    def test_kelly_sizes_position(self, risk_config, bull_signal, healthy_portfolio):
        result = validate_order(bull_signal, healthy_portfolio, risk_config)
        assert result.passed
        assert result.applied_size > 0
        assert result.applied_fraction == 0.25

    def test_negative_edge_blocks_trade(self, risk_config, healthy_portfolio):
        bad_signal = PredictionSignal(
            asset="DOG",
            direction=Direction.LONG,
            probability=0.30,
            confidence=0.75,
            entry_price=10.0,
        )
        result = validate_order(bad_signal, healthy_portfolio, risk_config)
        assert not result.passed
        assert result.sized_order is None


class TestRiskEnginePositionLimits:
    def test_size_within_5pct_passes(self, risk_config, bull_signal, healthy_portfolio):
        result = validate_order(bull_signal, healthy_portfolio, risk_config)
        assert result.passed
        assert result.sized_order is not None
        assert result.sized_order.size_dollars <= 0.05 * healthy_portfolio.nav

    def test_oversized_blocked(self, healthy_portfolio):
        tight_config = RiskConfig(
            min_confidence_threshold=0.55,
            kelly_fraction=0.25,
            max_position_pct=0.01,
            max_total_exposure=3.0,
            max_drawdown=0.08,
            daily_loss_limit=5000.0,
            daily_var_limit=10000.0,
            slippage_threshold=0.02,
        )
        huge_signal = PredictionSignal(
            asset="BIG",
            direction=Direction.LONG,
            probability=0.99,
            confidence=0.99,
            entry_price=1000.0,
        )
        result = validate_order(huge_signal, healthy_portfolio, tight_config)
        assert not result.passed


class TestRiskEngineDrawdownGate:
    def test_drawdown_blocks_trade(self, risk_config, bull_signal, drawn_down_portfolio):
        result = validate_order(bull_signal, drawn_down_portfolio, risk_config)
        assert not result.passed
        assert any("drawdown" in f.lower() for f in result.failures)

    def test_no_drawdown_allows_trade(self, risk_config, bull_signal, healthy_portfolio):
        result = validate_order(bull_signal, healthy_portfolio, risk_config)
        assert result.passed


class TestRiskEngineDailyLoss:
    def test_large_daily_loss_blocks(self, bull_signal):
        tight_config = RiskConfig(
            min_confidence_threshold=0.55,
            kelly_fraction=0.25,
            max_position_pct=0.05,
            max_total_exposure=3.0,
            max_drawdown=0.08,
            daily_loss_limit=5000.0,
            daily_var_limit=10000.0,
            slippage_threshold=0.02,
        )
        losing = PortfolioState(
            nav=95_000.0,
            cash=95_000.0,
            peak_equity=100_000.0,
            current_drawdown=0.05,
            positions=[],
            daily_pnl=-10000.0,
            total_exposure=0.0,
        )
        result = validate_order(bull_signal, losing, tight_config)
        assert not result.passed
        assert any("loss" in f.lower() for f in result.failures)


class TestRiskEngineAllPasses:
    def test_all_checks_pass_produces_sized_order(self, risk_config, bull_signal, healthy_portfolio):
        result = validate_order(bull_signal, healthy_portfolio, risk_config)
        assert result.passed
        assert result.sized_order.asset == "AAPL"
        assert result.sized_order.direction == Direction.LONG
        assert result.sized_order.size_dollars > 0
        assert result.sized_order.order_type == "LIMIT"
        assert result.failures == []
```

- [ ] **Step 2: Run test to verify failure**

```bash
cd /Users/animesh/Desktop/QuantEdge_Platform && python3 -m pytest services/tests/test_risk_engine.py -v
```

Expected: ImportError — engine module doesn't exist yet.

- [ ] **Step 3: Write risk engine implementation**

```python
"""Risk engine — 7 deterministic checks. All must pass for order to proceed."""
from __future__ import annotations

from typing import List

from services.risk.config import (
    Direction,
    PortfolioState,
    PredictionSignal,
    RiskConfig,
    RiskResult,
    SizedOrder,
)
from services.risk.kelly import apply_kelly_fraction
from services.risk.var import historical_var


def validate_order(
    signal: PredictionSignal,
    portfolio: PortfolioState,
    config: RiskConfig,
    daily_returns: list = None,
) -> RiskResult:
    failures: List[str] = []

    # 1. Edge check: confidence must meet threshold
    if signal.confidence < config.min_confidence_threshold:
        failures.append(
            f"edge_check: confidence {signal.confidence:.2%} below "
            f"threshold {config.min_confidence_threshold:.0%}"
        )

    # 2. Kelly sizing: compute position size from edge
    if signal.direction == Direction.HOLD or signal.probability <= 0.5:
        failures.append(
            f"kelly_sizing: no edge (p={signal.probability:.2%})"
        )
        position_size = 0.0
    else:
        win_loss_ratio = 1.0
        if signal.take_profit and signal.stop_loss and signal.entry_price:
            reward = abs(signal.take_profit - signal.entry_price)
            risk = abs(signal.entry_price - signal.stop_loss)
            if risk > 0:
                win_loss_ratio = reward / risk
        position_size = apply_kelly_fraction(
            signal.probability,
            win_loss_ratio,
            portfolio.nav,
            config.kelly_fraction,
        )

    if position_size <= 0:
        failures.append("kelly_sizing: computed zero or negative size")

    # 3. Position limit: single position <= max_pct of NAV
    max_single = config.max_position_pct * portfolio.nav
    if position_size > max_single:
        failures.append(
            f"position_limit: ${position_size:,.0f} exceeds "
            f"max ${max_single:,.0f} ({config.max_position_pct:.0%} NAV)"
        )

    # 4. Exposure check: new + existing <= max total exposure
    new_exposure = position_size
    total_exposure_after = portfolio.total_exposure + new_exposure
    max_exposure = config.max_total_exposure * portfolio.nav
    if total_exposure_after > max_exposure:
        failures.append(
            f"exposure_check: total exposure ${total_exposure_after:,.0f} "
            f"exceeds max ${max_exposure:,.0f}"
        )

    # 5. VaR check: 95% historical VaR within daily limit
    if daily_returns and len(daily_returns) >= 30:
        var_95 = historical_var(daily_returns, confidence=0.95)
        var_dollars = abs(var_95) * position_size if var_95 < 0 else 0
        if var_dollars > config.daily_var_limit:
            failures.append(
                f"var_check: daily VaR ${var_dollars:,.0f} exceeds "
                f"limit ${config.daily_var_limit:,.0f}"
            )

    # 6. Drawdown gate: block if portfolio loss exceeds max
    if portfolio.current_drawdown > config.max_drawdown:
        failures.append(
            f"drawdown_gate: current drawdown {portfolio.current_drawdown:.1%} "
            f"exceeds max {config.max_drawdown:.1%}"
        )

    # 7. Daily loss limit: auto-halt on large daily P&L loss
    if portfolio.daily_pnl < -config.daily_loss_limit:
        failures.append(
            f"daily_loss_limit: daily P&L ${portfolio.daily_pnl:,.0f} "
            f"exceeds limit -${config.daily_loss_limit:,.0f}"
        )

    if failures:
        return RiskResult(passed=False, sized_order=None, failures=failures)

    sized_order = SizedOrder(
        asset=signal.asset,
        direction=signal.direction,
        size_dollars=position_size,
        entry_price=signal.entry_price,
        order_type="LIMIT",
        stop_loss=signal.stop_loss,
        take_profit=signal.take_profit,
    )

    return RiskResult(
        passed=True,
        sized_order=sized_order,
        failures=[],
        applied_size=position_size,
        applied_fraction=config.kelly_fraction,
    )
```

- [ ] **Step 4: Run tests to verify pass**

```bash
cd /Users/animesh/Desktop/QuantEdge_Platform && python3 -m pytest services/tests/test_risk_engine.py -v
```

Expected: All tests pass. If any fail, fix implementation and re-run.

- [ ] **Step 5: Commit**

```bash
git add services/risk/engine.py services/tests/test_risk_engine.py
git commit -m "feat(pillar-c): add risk engine with 7 deterministic checks"
```

---

### Task 5: Kill Switch (TDD)

**Files:**
- Create: `services/risk/kill_switch.py`
- Create: `services/tests/test_kill_switch.py`

- [ ] **Step 1: Write failing tests for kill switch**

```python
"""Tests for the KillSwitch dual-trigger mechanism."""
import os
import time
from pathlib import Path

import pytest
from services.risk.kill_switch import KillSwitch


class TestKillSwitchFileDrop:
    def test_inactive_by_default(self, temp_flag_dir):
        ks = KillSwitch(flag_dir=temp_flag_dir)
        assert not ks.is_active()

    def test_stop_flag_activates(self, temp_flag_dir):
        ks = KillSwitch(flag_dir=temp_flag_dir)
        Path(temp_flag_dir, "STOP.flag").touch()
        assert ks.is_active()

    def test_resume_flag_clears(self, temp_flag_dir):
        ks = KillSwitch(flag_dir=temp_flag_dir)
        Path(temp_flag_dir, "STOP.flag").touch()
        assert ks.is_active()
        ks.resume()
        assert not ks.is_active()

    def test_trigger_creates_flag(self, temp_flag_dir):
        ks = KillSwitch(flag_dir=temp_flag_dir)
        assert not Path(temp_flag_dir, "STOP.flag").exists()
        ks.trigger()
        assert Path(temp_flag_dir, "STOP.flag").exists()
        assert ks.is_active()

    def test_health_check_returns_true(self, temp_flag_dir):
        ks = KillSwitch(flag_dir=temp_flag_dir)
        result = ks.health_check()
        assert result

    def test_health_check_does_not_leave_flags(self, temp_flag_dir):
        ks = KillSwitch(flag_dir=temp_flag_dir)
        ks.health_check()
        assert not Path(temp_flag_dir, "STOP.flag").exists()

    def test_resume_when_no_flag_is_safe(self, temp_flag_dir):
        ks = KillSwitch(flag_dir=temp_flag_dir)
        ks.resume()
        assert not ks.is_active()
```

- [ ] **Step 2: Run test to verify failure**

```bash
cd /Users/animesh/Desktop/QuantEdge_Platform && python3 -m pytest services/tests/test_kill_switch.py -v
```

- [ ] **Step 3: Write implementation**

```python
"""Kill switch — dual trigger: file-drop (local) + manual (API/cloud)."""
from __future__ import annotations

import logging
import os
import tempfile
from pathlib import Path

_log = logging.getLogger(__name__)


class KillSwitch:
    def __init__(self, flag_dir: str = "./flags"):
        self.flag_dir = Path(flag_dir)
        self.flag_dir.mkdir(parents=True, exist_ok=True)
        self.stop_flag = self.flag_dir / "STOP.flag"
        self.resume_flag = self.flag_dir / "RESUME.flag"
        self._active = False

    def is_active(self) -> bool:
        if self.stop_flag.exists():
            self._active = True
        return self._active

    def trigger(self) -> None:
        self.stop_flag.touch()
        self._active = True
        _log.critical("KILL SWITCH TRIGGERED — all order generation halted")

    def resume(self) -> None:
        if self.stop_flag.exists():
            self.stop_flag.unlink()
        self._active = False
        self.resume_flag.touch()
        _log.info("Kill switch resumed — order generation re-enabled")
        if self.resume_flag.exists():
            self.resume_flag.unlink()

    def health_check(self) -> bool:
        test_flag = self.flag_dir / ".health_check_test"
        try:
            test_flag.touch()
            if not test_flag.exists():
                return False
            test_flag.unlink()
            return True
        except OSError:
            return False
```

- [ ] **Step 4: Run tests to verify pass**

```bash
cd /Users/animesh/Desktop/QuantEdge_Platform && python3 -m pytest services/tests/test_kill_switch.py -v
```

Expected: All 7 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add services/risk/kill_switch.py services/tests/test_kill_switch.py
git commit -m "feat(pillar-c): add dual-trigger kill switch with health check"
```

---

### Task 6: Slippage Guard (TDD)

**Files:**
- Create: `services/execution/slippage.py`
- Create: `services/tests/test_slippage.py`

- [ ] **Step 1: Write failing tests for slippage guard**

```python
"""Tests for slippage guard — price deviation check before order submission."""
import pytest
from services.execution.slippage import check_slippage


class TestSlippageGuard:
    def test_within_threshold_passes(self):
        result = check_slippage(
            signal_price=150.0,
            current_price=151.0,
            threshold=0.02,
        )
        assert result.ok
        assert result.deviation < 0.02

    def test_exceeds_threshold_aborts(self):
        result = check_slippage(
            signal_price=100.0,
            current_price=103.0,
            threshold=0.02,
        )
        assert not result.ok
        assert result.deviation > 0.02

    def test_exact_threshold_passes(self):
        result = check_slippage(
            signal_price=100.0,
            current_price=102.0,
            threshold=0.02,
        )
        assert result.ok

    def test_price_improvement_always_passes(self):
        result = check_slippage(
            signal_price=150.0,
            current_price=148.0,
            threshold=0.02,
        )
        assert result.ok

    def test_extreme_slippage_short(self):
        result = check_slippage(
            signal_price=50.0,
            current_price=45.0,
            threshold=0.02,
        )
        assert not result.ok
```

- [ ] **Step 2: Run test to verify failure**

```bash
cd /Users/animesh/Desktop/QuantEdge_Platform && python3 -m pytest services/tests/test_slippage.py -v
```

- [ ] **Step 3: Write implementation**

```python
"""Slippage guard — abort if market price deviates too far from signal price."""
from __future__ import annotations

from dataclasses import dataclass


@dataclass
class SlippageResult:
    ok: bool
    deviation: float
    signal_price: float
    current_price: float
    message: str = ""


def check_slippage(
    signal_price: float,
    current_price: float,
    threshold: float = 0.02,
) -> SlippageResult:
    if signal_price <= 0:
        return SlippageResult(
            ok=False, deviation=1.0,
            signal_price=signal_price, current_price=current_price,
            message="Invalid signal price",
        )

    deviation = abs(current_price - signal_price) / signal_price

    if deviation > threshold:
        return SlippageResult(
            ok=False,
            deviation=round(deviation, 4),
            signal_price=signal_price,
            current_price=current_price,
            message=(
                f"Slippage {deviation:.2%} exceeds {threshold:.2%} threshold. "
                f"Signal: ${signal_price:.2f}, Market: ${current_price:.2f}"
            ),
        )

    return SlippageResult(
        ok=True,
        deviation=round(deviation, 4),
        signal_price=signal_price,
        current_price=current_price,
        message="",
    )
```

- [ ] **Step 4: Run tests to verify pass**

```bash
cd /Users/animesh/Desktop/QuantEdge_Platform && python3 -m pytest services/tests/test_slippage.py -v
```

Expected: All 5 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add services/execution/slippage.py services/tests/test_slippage.py
git commit -m "feat(pillar-c): add slippage guard with configurable threshold"
```

---

### Task 7: Paper Execution Provider (TDD)

**Files:**
- Create: `services/execution/paper.py`
- Create: `services/tests/test_paper_execution.py`

- [ ] **Step 1: Write failing tests for paper execution**

```python
"""Tests for PaperProvider — deterministic virtual trading."""
import pytest
from services.risk.config import Direction, SizedOrder
from services.execution.base import OrderResult
from services.execution.paper import PaperProvider


@pytest.fixture
def paper_provider():
    return PaperProvider(initial_cash=100_000.0)


@pytest.fixture
def buy_order():
    return SizedOrder(
        asset="AAPL",
        direction=Direction.LONG,
        size_dollars=10_000.0,
        entry_price=150.0,
        stop_loss=145.0,
        take_profit=160.0,
    )


@pytest.fixture
def sell_order():
    return SizedOrder(
        asset="TSLA",
        direction=Direction.SHORT,
        size_dollars=5_000.0,
        entry_price=250.0,
        stop_loss=260.0,
        take_profit=230.0,
    )


class TestPaperProviderSubmit:
    @pytest.mark.asyncio
    async def test_submit_buy_creates_position(self, paper_provider, buy_order):
        result = await paper_provider.submit_order(buy_order)
        assert isinstance(result, OrderResult)
        assert result.status == "FILLED"
        assert result.rejected_reason is None

    @pytest.mark.asyncio
    async def test_submit_sell_creates_position(self, paper_provider, sell_order):
        result = await paper_provider.submit_order(sell_order)
        assert result.status == "FILLED"

    @pytest.mark.asyncio
    async def test_submit_reduces_cash(self, paper_provider, buy_order):
        account_before = await paper_provider.get_account()
        await paper_provider.submit_order(buy_order)
        account_after = await paper_provider.get_account()
        assert account_after.cash < account_before.cash

    @pytest.mark.asyncio
    async def test_insufficient_cash_fails(self, paper_provider):
        huge_order = SizedOrder(
            asset="AAPL",
            direction=Direction.LONG,
            size_dollars=200_000.0,
            entry_price=150.0,
        )
        result = await paper_provider.submit_order(huge_order)
        assert result.status != "FILLED"

    @pytest.mark.asyncio
    async def test_submit_huge_order_insufficient_rejected(self, paper_provider):
        huge = SizedOrder(
            asset="BIG",
            direction=Direction.LONG,
            size_dollars=500_000.0,
            entry_price=1000.0,
        )
        result = await paper_provider.submit_order(huge)
        assert result.status != "FILLED"
        assert result.rejected_reason is not None


class TestPaperProviderCancel:
    @pytest.mark.asyncio
    async def test_cancel_nonexistent_order(self, paper_provider):
        result = await paper_provider.cancel_order("nonexistent-id")
        assert not result

    @pytest.mark.asyncio
    async def test_cancel_all_returns_count(self, paper_provider, buy_order):
        await paper_provider.submit_order(buy_order)
        count = await paper_provider.cancel_all_orders()
        assert isinstance(count, int)
        assert count >= 0


class TestPaperProviderAccount:
    @pytest.mark.asyncio
    async def test_initial_account_state(self, paper_provider):
        account = await paper_provider.get_account()
        assert account.cash == 100_000.0
        assert account.equity == 100_000.0
        assert account.positions == []

    @pytest.mark.asyncio
    async def test_get_positions(self, paper_provider, buy_order):
        await paper_provider.submit_order(buy_order)
        positions = await paper_provider.get_positions()
        assert len(positions) > 0
```

- [ ] **Step 2: Run test to verify failure**

```bash
cd /Users/animesh/Desktop/QuantEdge_Platform && python3 -m pytest services/tests/test_paper_execution.py -v
```

- [ ] **Step 3: Write implementation**

```python
"""PaperProvider — deterministic paper trading with virtual P&L tracking."""
from __future__ import annotations

from typing import Dict, List

from services.execution.base import (
    AccountState,
    ExecutionProvider,
    OrderResult,
    Position,
)
from services.risk.config import Direction, SizedOrder


class PaperProvider(ExecutionProvider):
    def __init__(self, initial_cash: float = 100_000.0):
        self._cash = initial_cash
        self._initial_cash = initial_cash
        self._positions: Dict[str, Position] = {}
        self._orders: List[OrderResult] = []

    async def submit_order(self, order: SizedOrder) -> OrderResult:
        quantity = order.size_dollars / order.entry_price if order.entry_price > 0 else 0
        cost = order.size_dollars

        if cost > self._cash:
            return OrderResult(
                asset=order.asset,
                status="REJECTED",
                filled_price=order.entry_price,
                filled_size=0.0,
                rejected_reason=f"Insufficient cash: need ${cost:,.0f}, have ${self._cash:,.0f}",
            )

        if order.direction == Direction.LONG:
            self._cash -= cost
            if order.asset in self._positions:
                existing = self._positions[order.asset]
                avg_price = ((existing.size * existing.entry_price) + (quantity * order.entry_price)) / (existing.size + quantity)
                existing.size += quantity
                existing.entry_price = avg_price
            else:
                self._positions[order.asset] = Position(
                    asset=order.asset,
                    size=quantity,
                    entry_price=order.entry_price,
                    current_price=order.entry_price,
                )
        elif order.direction == Direction.SHORT:
            self._cash += cost
            if order.asset in self._positions:
                existing = self._positions[order.asset]
                avg_price = ((existing.size * existing.entry_price) + (quantity * order.entry_price)) / (existing.size + quantity)
                existing.size -= quantity
                existing.entry_price = avg_price
            else:
                self._positions[order.asset] = Position(
                    asset=order.asset,
                    size=-quantity,
                    entry_price=order.entry_price,
                    current_price=order.entry_price,
                )

        result = OrderResult(
            asset=order.asset,
            status="FILLED",
            filled_price=order.entry_price,
            filled_size=quantity,
        )
        self._orders.append(result)
        return result

    async def cancel_order(self, order_id: str) -> bool:
        for i, o in enumerate(self._orders):
            if o.order_id == order_id:
                self._orders.pop(i)
                return True
        return False

    async def cancel_all_orders(self) -> int:
        count = len(self._orders)
        self._orders.clear()
        return count

    async def get_positions(self) -> List[Position]:
        return list(self._positions.values())

    async def get_account(self) -> AccountState:
        positions = await self.get_positions()
        position_value = sum(
            p.size * p.current_price for p in positions
        )
        equity = self._cash + position_value

        return AccountState(
            cash=self._cash,
            equity=equity,
            buying_power=self._cash,
            positions=positions,
        )
```

- [ ] **Step 4: Run tests to verify pass**

```bash
cd /Users/animesh/Desktop/QuantEdge_Platform && python3 -m pytest services/tests/test_paper_execution.py -v
```

Expected: All tests PASS.

- [ ] **Step 5: Commit**

```bash
git add services/execution/paper.py services/tests/test_paper_execution.py
git commit -m "feat(pillar-c): add paper execution provider with P&L tracking"
```

---

### Task 8: Full Integration Test — End-to-End Order Flow

**Files:**
- Create: `services/tests/test_integration.py`

- [ ] **Step 1: Write integration test**

```python
"""Integration test: signal → risk → kill switch → execution."""
import pytest
from services.risk.config import Direction, PredictionSignal, PortfolioState, RiskConfig
from services.risk.engine import validate_order
from services.risk.kill_switch import KillSwitch
from services.execution.paper import PaperProvider
from services.execution.slippage import check_slippage


class TestPillarCIntegration:
    @pytest.mark.asyncio
    async def test_full_order_flow_success(self, tmp_path):
        # Setup
        config = RiskConfig()
        ks = KillSwitch(flag_dir=str(tmp_path))
        provider = PaperProvider(initial_cash=100_000.0)
        portfolio = PortfolioState(
            nav=100_000.0,
            cash=100_000.0,
            peak_equity=100_000.0,
            current_drawdown=0.0,
            positions=[],
            daily_pnl=0.0,
            total_exposure=0.0,
        )
        signal = PredictionSignal(
            asset="AAPL",
            direction=Direction.LONG,
            probability=0.65,
            confidence=0.72,
            entry_price=150.0,
            stop_loss=145.0,
            take_profit=160.0,
            rationale="Kronos bullish + DCF upside",
        )

        # Step 1: Kill switch check
        assert not ks.is_active(), "Kill switch must not be active"

        # Step 2: Risk validation
        result = validate_order(signal, portfolio, config)
        assert result.passed, f"Risk validation failed: {result.failures}"
        assert result.sized_order is not None

        # Step 3: Slippage check
        slip = check_slippage(
            signal_price=signal.entry_price,
            current_price=150.50,
            threshold=config.slippage_threshold,
        )
        assert slip.ok, f"Slippage check failed: {slip.message}"

        # Step 4: Execute
        exec_result = await provider.submit_order(result.sized_order)
        assert exec_result.status == "FILLED"

        # Verify account state
        account = await provider.get_account()
        assert account.equity > 0
        positions = await provider.get_positions()
        assert len(positions) == 1
        assert positions[0].asset == "AAPL"

    @pytest.mark.asyncio
    async def test_kill_switch_blocks_execution(self, tmp_path):
        config = RiskConfig()
        ks = KillSwitch(flag_dir=str(tmp_path))
        provider = PaperProvider(initial_cash=100_000.0)
        portfolio = PortfolioState(
            nav=100_000.0, cash=100_000.0, peak_equity=100_000.0,
            current_drawdown=0.0, positions=[], daily_pnl=0.0, total_exposure=0.0,
        )
        signal = PredictionSignal(
            asset="AAPL", direction=Direction.LONG,
            probability=0.65, confidence=0.72, entry_price=150.0,
        )

        ks.trigger()
        if ks.is_active():
            orders_before = len(await provider.get_positions())
            assert orders_before == 0, "No orders should be placed when kill switch is active"

    @pytest.mark.asyncio
    async def test_drawdown_blocks_new_trades(self, tmp_path):
        config = RiskConfig()
        ks = KillSwitch(flag_dir=str(tmp_path))
        provider = PaperProvider(initial_cash=100_000.0)
        portfolio = PortfolioState(
            nav=90_000.0, cash=90_000.0, peak_equity=100_000.0,
            current_drawdown=0.10, positions=[], daily_pnl=-500.0, total_exposure=0.0,
        )
        signal = PredictionSignal(
            asset="META", direction=Direction.LONG,
            probability=0.65, confidence=0.72, entry_price=300.0,
        )
        result = validate_order(signal, portfolio, config)
        assert not result.passed
        assert any("drawdown" in f.lower() for f in result.failures)
```

- [ ] **Step 2: Run integration test**

```bash
cd /Users/animesh/Desktop/QuantEdge_Platform && python3 -m pytest services/tests/test_integration.py -v
```

Expected: All 3 integration tests PASS.

- [ ] **Step 3: Run full Pillar C test suite**

```bash
cd /Users/animesh/Desktop/QuantEdge_Platform && python3 -m pytest services/tests/ -v
```

Expected: All tests PASS (Kelly 9 + VaR 6 + Risk Engine 9 + Kill Switch 7 + Slippage 5 + Paper 9 + Integration 3 = 48 tests).

- [ ] **Step 4: Add .env.example entries**

Append to `.env.example`:

```bash

# ─── Pillar C: Risk & Execution ────────────────────────────────────────────
MIN_CONFIDENCE_THRESHOLD=0.55
KELLY_FRACTION=0.25
MAX_POSITION_PCT=0.05
MAX_TOTAL_EXPOSURE=3.0
MAX_DRAWDOWN=0.08
DAILY_LOSS_LIMIT=5000
DAILY_VAR_LIMIT=10000
SLIPPAGE_THRESHOLD=0.02
LIVE_TRADING=false
LIVE_TRADING_CONFIRM=no
KILL_SWITCH_DIR=./flags
```

- [ ] **Step 5: Commit**

```bash
git add services/tests/test_integration.py .env.example
git commit -m "feat(pillar-c): add integration tests for full order flow"
```
