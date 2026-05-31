"""Tests for Prediction Aggregator."""
import pytest
from agents.prediction.schema import PredictionSignal, AggregationResult
from agents.prediction.config import PredictionConfig
from agents.prediction.calibration import CalibrationTracker
from agents.prediction.aggregator import PredictionAggregator


class TestCalibration:
    def test_brier_score_perfect(self):
        tracker = CalibrationTracker()
        tracker.record("test", "AAPL", 0.9, 1)
        tracker.record("test", "AAPL", 0.9, 1)
        assert tracker.brier_score("test") == pytest.approx(0.01)

    def test_brier_score_worst(self):
        tracker = CalibrationTracker()
        tracker.record("test", "AAPL", 0.9, 0)
        tracker.record("test", "AAPL", 0.9, 0)
        assert tracker.brier_score("test") == pytest.approx(0.81)

    def test_empty_returns_zero(self):
        tracker = CalibrationTracker()
        assert tracker.brier_score() == 0.0

    def test_get_agent_scores(self):
        tracker = CalibrationTracker()
        tracker.record("agent_a", "AAPL", 0.7, 1)
        tracker.record("agent_b", "MSFT", 0.6, 0)
        scores = tracker.get_agent_scores()
        assert "agent_a" in scores
        assert "agent_b" in scores


class TestPredictionConfig:
    def test_weights_sum_to_one(self):
        cfg = PredictionConfig()
        total = cfg.kronos_weight + cfg.llm_weight + cfg.research_weight
        assert abs(total - 1.0) < 0.001

    def test_defaults(self):
        cfg = PredictionConfig()
        assert cfg.min_confidence == 0.55
        assert cfg.tp_pct == 0.04
        assert cfg.sl_pct == 0.02


class TestPredictionAggregator:
    def test_kronos_to_probability_bullish(self):
        agg = PredictionAggregator()
        prob = agg._kronos_to_probability(0.10)
        assert prob > 0.5

    def test_kronos_to_probability_bearish(self):
        agg = PredictionAggregator()
        prob = agg._kronos_to_probability(-0.10)
        assert prob < 0.5

    def test_kronos_to_probability_flat(self):
        agg = PredictionAggregator()
        prob = agg._kronos_to_probability(0.0)
        assert prob == 0.5

    def test_aggregate_no_api_key(self):
        from llm.config import LLMConfig
        from llm.deepseek import DeepSeekProvider
        agg = PredictionAggregator(provider=DeepSeekProvider(LLMConfig(deepseek_api_key="")))
        result = agg.aggregate(
            asset="AAPL",
            entry_price=150.0,
            kronos_forecast={"forecast_return": 3.5},
            fundamental_report={"recommendation": "BUY", "upside_pct": 10.0, "confidence": 0.75},
        )
        assert isinstance(result, AggregationResult)
        assert result.signal.asset == "AAPL"

    def test_aggregate_budget_exceeded(self):
        from llm.budget import LLMBudget
        from llm.config import LLMConfig
        cfg = LLMConfig(daily_budget_usd=0.001)
        budget = LLMBudget(config=cfg)
        budget.record_call("prediction", 10000, 5000, 0.01)
        agg = PredictionAggregator(budget=budget)
        result = agg.aggregate("AAPL", 150.0)
        assert result.signal.direction == "HOLD"
        assert "budget" in result.signal.rationale.lower()

    def test_compute_ensemble_all_bullish(self):
        agg = PredictionAggregator()
        ensemble = agg._compute_ensemble(
            0.70,
            0.75,
            {"recommendation": "BUY"},
            {"verdict": "UPGRADE"},
            {"sentiment": "BULLISH"},
        )
        assert ensemble > 0.65

    def test_record_outcome(self):
        agg = PredictionAggregator()
        agg.record_outcome("prediction", "AAPL", 0.70, True)
        assert agg.calibration.count("prediction") == 1
