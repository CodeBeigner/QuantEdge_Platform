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
