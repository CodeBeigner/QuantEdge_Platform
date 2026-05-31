"""Tests for Sentiment and Sector agents."""
import pytest
from agents.sentiment.schema import SentimentSignal
from agents.sentiment.agent import SentimentAnalyst
from agents.sector.schema import MacroRegimeReport
from agents.sector.agent import SectorAnalyst


class TestSentimentSignal:
    def test_creation(self):
        sig = SentimentSignal(symbol="AAPL", sentiment="BULLISH", confidence=0.75, source_count=15)
        assert sig.sentiment == "BULLISH"
        assert sig.confidence == 0.75
        assert sig.source_count == 15

    def test_defaults(self):
        sig = SentimentSignal(symbol="MSFT")
        assert sig.sentiment == "NEUTRAL"


class TestSentimentAnalyst:
    def test_analyze_no_api_key(self):
        from llm.config import LLMConfig
        from llm.deepseek import DeepSeekProvider
        agent = SentimentAnalyst(provider=DeepSeekProvider(LLMConfig(deepseek_api_key="")))
        sig = agent.analyze(symbol="AAPL", headlines=["AAPL beats earnings", "iPhone sales strong", "Services growth accelerates"], current_price=150.0)
        assert isinstance(sig, SentimentSignal)
        assert sig.symbol == "AAPL"

    def test_analyze_budget_exceeded(self):
        from llm.budget import LLMBudget
        from llm.config import LLMConfig
        cfg = LLMConfig(daily_budget_usd=0.001)
        budget = LLMBudget(config=cfg)
        budget.record_call("sentiment", 10000, 5000, 0.01)
        agent = SentimentAnalyst(budget=budget)
        sig = agent.analyze(symbol="AAPL", headlines=["headline"])
        assert "budget" in sig.narrative_summary.lower()


class TestMacroRegimeReport:
    def test_defaults(self):
        report = MacroRegimeReport()
        assert report.regime == "NEUTRAL"
        assert report.vix_level == "NORMAL"

    def test_creation(self):
        report = MacroRegimeReport(regime="RISK_ON", vix_level="LOW", rate_environment="FALLING")
        assert report.regime == "RISK_ON"
        assert report.credit_spreads == "NORMAL"


class TestSectorAnalyst:
    def test_analyze_no_api_key(self):
        from llm.config import LLMConfig
        from llm.deepseek import DeepSeekProvider
        agent = SectorAnalyst(provider=DeepSeekProvider(LLMConfig(deepseek_api_key="")))
        report = agent.analyze(context="Tech leading, Energy lagging, VIX at 14")
        assert isinstance(report, MacroRegimeReport)

    def test_analyze_budget_exceeded(self):
        from llm.budget import LLMBudget
        from llm.config import LLMConfig
        cfg = LLMConfig(daily_budget_usd=0.001)
        budget = LLMBudget(config=cfg)
        budget.record_call("sector", 10000, 5000, 0.01)
        agent = SectorAnalyst(budget=budget)
        report = agent.analyze(context="Some context")
        assert "budget" in report.breadth_summary.lower()
