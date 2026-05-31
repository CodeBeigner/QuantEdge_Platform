"""Tests for Fundamental Analyst Agent."""
import pytest
from agents.fundamental.schema import FundamentalReport, ValuationRange, CompData
from agents.fundamental.agent import FundamentalAnalyst, extract_json


class TestSchema:
    def test_fundamental_report_creation(self):
        report = FundamentalReport(
            symbol="AAPL",
            company_name="Apple Inc.",
            current_price=150.0,
            implied_price=ValuationRange(bear=130, base=160, bull=190),
            recommendation="BUY",
            confidence=0.75,
        )
        assert report.symbol == "AAPL"
        assert report.recommendation == "BUY"
        assert report.implied_price.base == 160.0

    def test_upside_calculation(self):
        report = FundamentalReport(
            symbol="AAPL",
            current_price=100.0,
            implied_price=ValuationRange(bear=80, base=120, bull=150),
            upside_pct=20.0,
        )
        assert report.upside_pct == 20.0


class TestExtractJson:
    def test_extracts_plain_json(self):
        text = '{"key": "value"}'
        assert extract_json(text) == '{"key": "value"}'

    def test_extracts_json_from_markdown(self):
        text = '```json\n{"key": "value"}\n```'
        result = extract_json(text)
        assert '"key"' in result

    def test_extracts_json_from_code_block(self):
        text = 'Here is data: ```\n{"key": "value"}\n``` end'
        result = extract_json(text)
        assert '"key"' in result


class TestFundamentalAnalyst:
    def test_creates_with_default_provider(self):
        agent = FundamentalAnalyst()
        assert agent.provider is not None
        assert agent.budget is not None

    def test_analyze_no_api_key(self):
        from llm.config import LLMConfig
        from llm.deepseek import DeepSeekProvider
        agent = FundamentalAnalyst(provider=DeepSeekProvider(LLMConfig(deepseek_api_key="")))
        report = agent.analyze("AAPL", 150.0, context="Strong earnings")
        assert isinstance(report, FundamentalReport)
        assert report.symbol == "AAPL"

    def test_analyze_with_budget_exceeded(self):
        from llm.budget import LLMBudget
        from llm.config import LLMConfig
        cfg = LLMConfig(daily_budget_usd=0.001)
        budget = LLMBudget(config=cfg)
        budget.record_call("fundamental", 100000, 50000, 0.01)
        agent = FundamentalAnalyst(budget=budget)
        report = agent.analyze("AAPL", 150.0)
        assert report.recommendation == "HOLD"
        assert "budget" in report.summary.lower()
