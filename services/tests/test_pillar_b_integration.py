"""Integration test: research agents -> prediction aggregator -> risk engine."""
import pytest
from agents.prediction.aggregator import PredictionAggregator
from agents.prediction.schema import AggregationResult, PredictionSignal
from services.risk.engine import validate_order
from services.risk.config import PortfolioState, PredictionSignal as RiskPredictionSignal, Direction, RiskConfig


class TestPillarBIntegration:
    def test_prediction_aggregator_no_llm(self):
        """Prediction aggregator works even without LLM API key."""
        from llm.config import LLMConfig
        from llm.deepseek import DeepSeekProvider
        agg = PredictionAggregator(provider=DeepSeekProvider(LLMConfig(deepseek_api_key="")))
        result = agg.aggregate(
            asset="AAPL",
            entry_price=150.0,
            kronos_forecast={"forecast_return": 5.2},
            fundamental_report={"recommendation": "BUY", "upside_pct": 12.0, "confidence": 0.8},
            earnings_signal={"verdict": "UPGRADE", "beats_misses": "Beat EPS by 4%"},
            sentiment_signal={"sentiment": "BULLISH", "confidence": 0.7},
            macro_context="Tech leading, rates stable",
        )
        assert isinstance(result, AggregationResult)
        assert result.signal.asset == "AAPL"

    def test_prediction_to_risk_engine_compatible(self):
        """PredictionSignal from aggregator must be compatible with risk engine."""
        pred = PredictionSignal(
            asset="AAPL",
            direction="LONG",
            probability=0.65,
            confidence=0.72,
            entry_price=150.0,
            stop_loss=145.0,
            take_profit=160.0,
            rationale="Kronos bullish + fundamentals support",
        )

        risk_signal = RiskPredictionSignal(
            asset=pred.asset,
            direction=Direction.LONG if pred.direction == "LONG" else Direction.SHORT,
            probability=pred.probability,
            confidence=pred.confidence,
            entry_price=pred.entry_price,
            stop_loss=pred.stop_loss,
            take_profit=pred.take_profit,
            rationale=pred.rationale,
        )

        portfolio = PortfolioState(
            nav=100_000.0, cash=100_000.0, peak_equity=100_000.0,
            current_drawdown=0.0, positions=[], daily_pnl=0.0, total_exposure=0.0,
        )

        config = RiskConfig(max_position_pct=0.15)
        result = validate_order(risk_signal, portfolio, config)
        assert result.passed
        assert result.sized_order is not None

    def test_hold_signal_no_order(self):
        """HOLD signal should not produce a sized order."""
        pred = PredictionSignal(
            asset="META",
            direction="HOLD",
            probability=0.52,
            confidence=0.48,
            entry_price=300.0,
            rationale="Conflicting signals, no edge",
        )

        risk_signal = RiskPredictionSignal(
            asset=pred.asset,
            direction=Direction.HOLD,
            probability=pred.probability,
            confidence=pred.confidence,
            entry_price=pred.entry_price,
            rationale=pred.rationale,
        )

        portfolio = PortfolioState(
            nav=100_000.0, cash=100_000.0, peak_equity=100_000.0,
            current_drawdown=0.0, positions=[], daily_pnl=0.0, total_exposure=0.0,
        )

        config = RiskConfig()
        result = validate_order(risk_signal, portfolio, config)
        assert not result.passed
        assert result.sized_order is None

    def test_brier_calibration_flow(self):
        """Calibration tracker correctly records outcomes."""
        agg = PredictionAggregator()
        for i in range(10):
            agg.record_outcome("prediction", "AAPL", 0.70, i < 7)

        brier = agg.calibration.brier_score("prediction")
        assert 0.0 < brier < 1.0
        assert agg.calibration.count("prediction") == 10
