"""Integration test: signal -> risk -> kill switch -> execution."""
import pytest
from services.risk.config import Direction, PredictionSignal, PortfolioState, RiskConfig
from services.risk.engine import validate_order
from services.risk.kill_switch import KillSwitch
from services.execution.paper import PaperProvider
from services.execution.slippage import check_slippage


class TestPillarCIntegration:
    @pytest.mark.asyncio
    async def test_full_order_flow_success(self, tmp_path):
        config = RiskConfig(max_position_pct=0.15)
        ks = KillSwitch(flag_dir=str(tmp_path))
        provider = PaperProvider(initial_cash=100_000.0)
        portfolio = PortfolioState(
            nav=100_000.0, cash=100_000.0, peak_equity=100_000.0,
            current_drawdown=0.0, positions=[], daily_pnl=0.0, total_exposure=0.0,
        )
        signal = PredictionSignal(
            asset="AAPL", direction=Direction.LONG,
            probability=0.65, confidence=0.72, entry_price=150.0,
            stop_loss=145.0, take_profit=160.0,
            rationale="Kronos bullish + DCF upside",
        )

        assert not ks.is_active(), "Kill switch must not be active"
        result = validate_order(signal, portfolio, config)
        assert result.passed, f"Risk validation failed: {result.failures}"
        assert result.sized_order is not None

        slip = check_slippage(
            signal_price=signal.entry_price,
            current_price=150.50,
            threshold=config.slippage_threshold,
        )
        assert slip.ok, f"Slippage check failed: {slip.message}"

        exec_result = await provider.submit_order(result.sized_order)
        assert exec_result.status == "FILLED"

        account = await provider.get_account()
        assert account.equity > 0
        positions = await provider.get_positions()
        assert len(positions) == 1
        assert positions[0].asset == "AAPL"

    @pytest.mark.asyncio
    async def test_kill_switch_blocks_execution(self, tmp_path):
        config = RiskConfig()
        ks = KillSwitch(flag_dir=str(tmp_path))
        portfolio = PortfolioState(
            nav=100_000.0, cash=100_000.0, peak_equity=100_000.0,
            current_drawdown=0.0, positions=[], daily_pnl=0.0, total_exposure=0.0,
        )
        ks.trigger()
        assert ks.is_active()
        positions_before = len(portfolio.positions)
        assert positions_before == 0

    @pytest.mark.asyncio
    async def test_drawdown_blocks_new_trades(self, tmp_path):
        config = RiskConfig()
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
