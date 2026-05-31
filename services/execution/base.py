"""Execution provider abstract interface and result types."""
from __future__ import annotations

import uuid
from abc import ABC, abstractmethod
from dataclasses import dataclass, field
from datetime import datetime
from typing import TYPE_CHECKING, List, Optional

if TYPE_CHECKING:
    from services.risk.config import SizedOrder


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
