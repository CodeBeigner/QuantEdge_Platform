"""Tests for Earnings/Catalyst Agent."""
import pytest
from agents.earnings.schema import EarningsSignal
from agents.earnings.agent import EarningsAnalyst


class TestEarningsSignal:
    def test_creation(self):
        sig = EarningsSignal(symbol="AAPL", verdict="UPGRADE", confidence=0.8)
        assert sig.symbol == "AAPL"
        assert sig.verdict == "UPGRADE"

    def test_defaults(self):
        sig = EarningsSignal(symbol="MSFT")
        assert sig.verdict == "NEUTRAL"
        assert sig.tone == "NEUTRAL"


class TestEarningsAnalyst:
    def test_add_and_get_upcoming(self):
        agent = EarningsAnalyst()
        agent.add_event("AAPL", "2099-12-31", "earnings")
        events = agent.get_upcoming(symbol="AAPL")
        assert len(events) == 1
        assert events[0]["symbol"] == "AAPL"

    def test_get_upcoming_filters_past(self):
        agent = EarningsAnalyst()
        agent.add_event("TSLA", "2020-01-01", "earnings")
        events = agent.get_upcoming(symbol="TSLA")
        assert len(events) == 0

    def test_analyze_no_api_key(self):
        from llm.config import LLMConfig
        from llm.deepseek import DeepSeekProvider
        agent = EarningsAnalyst(provider=DeepSeekProvider(LLMConfig(deepseek_api_key="")))
        sig = agent.analyze("AAPL", "AAPL beat EPS by 5%. Revenue grew 8% YoY. Guidance raised for next quarter.", 150.0)
        assert isinstance(sig, EarningsSignal)
        assert sig.symbol == "AAPL"

    def test_analyze_budget_exceeded(self):
        from llm.budget import LLMBudget
        from llm.config import LLMConfig
        cfg = LLMConfig(daily_budget_usd=0.001)
        budget = LLMBudget(config=cfg)
        budget.record_call("earnings", 10000, 5000, 0.01)
        agent = EarningsAnalyst(budget=budget)
        sig = agent.analyze("AAPL", "Transcript text", 150.0)
        assert sig.verdict == "NEUTRAL"
        assert "budget" in sig.summary.lower()
