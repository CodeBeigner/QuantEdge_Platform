"""PaperProvider — deterministic paper trading with virtual P&L tracking."""
from __future__ import annotations

from typing import Dict, List, Optional

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
        if order.entry_price <= 0:
            return OrderResult(
                asset=order.asset,
                status="REJECTED",
                filled_price=order.entry_price,
                filled_size=0.0,
                rejected_reason="Invalid entry price",
            )
        quantity = order.size_dollars / order.entry_price
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
                existing.size -= quantity
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
