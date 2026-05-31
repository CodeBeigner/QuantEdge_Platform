"""Tests for Pillar D: Trade Ledger, Post-Mortem, Knowledge Base."""
import json
import os
import tempfile
import uuid
from pathlib import Path

import pytest

from data.ledger.schema import TradeRecord
from data.ledger.store import TradeLedger
from agents.postmortem.schema import PostMortemResult
from agents.postmortem.agent import PostMortemAgent
from data.knowledge_base.store import KnowledgeBase


@pytest.fixture
def temp_ledger_dir():
    with tempfile.TemporaryDirectory() as d:
        yield d


@pytest.fixture
def sample_trade():
    return TradeRecord(
        trade_id=str(uuid.uuid4()),
        asset="AAPL",
        direction="LONG",
        entry_price=150.0,
        size_dollars=10_000.0,
        quantity=66,
        model_probability=0.65,
        confidence=0.72,
        entry_timestamp="2026-01-15T10:00:00",
        rationale="Kronos forecast bullish",
    )


class TestTradeRecord:
    def test_close_win(self, sample_trade):
        sample_trade.close(exit_price=160.0, exit_timestamp="2026-01-15T14:00:00")
        assert sample_trade.outcome == "WIN"
        assert sample_trade.pnl > 0
        assert sample_trade.time_held_hours == 4.0

    def test_close_loss(self, sample_trade):
        sample_trade.close(exit_price=140.0, exit_timestamp="2026-01-16T10:00:00")
        assert sample_trade.outcome == "LOSS"
        assert sample_trade.pnl < 0

    def test_close_breakeven(self, sample_trade):
        sample_trade.close(exit_price=150.01)
        assert sample_trade.outcome == "BREAKEVEN"

    def test_defaults(self):
        record = TradeRecord(trade_id="t1", asset="MSFT", direction="LONG", entry_price=400.0)
        assert record.outcome == "OPEN"
        assert record.schema_version == "1.0.0"


class TestTradeLedger:
    def test_log_and_read(self, temp_ledger_dir, sample_trade):
        ledger = TradeLedger(ledger_path=f"{temp_ledger_dir}/trades.jsonl")
        ledger.log(sample_trade)
        records = ledger.read_all()
        assert len(records) == 1
        assert records[0]["asset"] == "AAPL"

    def test_stats(self, temp_ledger_dir, sample_trade):
        ledger = TradeLedger(ledger_path=f"{temp_ledger_dir}/trades.jsonl")
        sample_trade.close(exit_price=160.0)
        ledger.log(sample_trade)
        stats = ledger.stats()
        assert stats["total_trades"] == 1
        assert stats["win_rate"] == 1.0

    def test_empty_stats(self, temp_ledger_dir):
        ledger = TradeLedger(ledger_path=f"{temp_ledger_dir}/trades.jsonl")
        stats = ledger.stats()
        assert stats["total_trades"] == 0

    def test_get_by_symbol(self, temp_ledger_dir, sample_trade):
        ledger = TradeLedger(ledger_path=f"{temp_ledger_dir}/trades.jsonl")
        ledger.log(sample_trade)
        results = ledger.get_by_symbol("AAPL")
        assert len(results) == 1


class TestPostMortem:
    def test_analyze_model_error(self, sample_trade):
        sample_trade.model_probability = 0.48
        agent = PostMortemAgent()
        result = agent.analyze(sample_trade)
        assert result.classification == "model_error"

    def test_analyze_win(self, sample_trade):
        sample_trade.close(exit_price=155.0)
        agent = PostMortemAgent()
        result = agent.analyze(sample_trade)
        assert result.classification == "model_success"

    def test_analyze_execution_error(self, sample_trade):
        sample_trade.close(exit_price=145.0)
        agent = PostMortemAgent()
        result = agent.analyze(sample_trade, market_context="Major slippage on fill")
        assert result.classification == "execution_error"

    def test_consolidate(self, sample_trade):
        sample_trade.close(exit_price=155.0)
        agent = PostMortemAgent()
        result = agent.consolidate([sample_trade])
        assert result["total"] == 1
        assert result["total_pnl"] > 0
        assert len(result["key_learnings"]) > 0


class TestKnowledgeBase:
    def test_add_and_get_lessons(self, tmp_path):
        kb = KnowledgeBase(base_path=str(tmp_path))
        kb.add_lesson("AAPL", "Check pre-market liquidity", "execution_error")
        lessons = kb.get_lessons()
        assert len(lessons) == 1
        assert lessons[0]["asset"] == "AAPL"

    def test_filter_by_asset(self, tmp_path):
        kb = KnowledgeBase(base_path=str(tmp_path))
        kb.add_lesson("AAPL", "Lesson 1", "model_error")
        kb.add_lesson("TSLA", "Lesson 2", "timing_error")
        aapl = kb.get_lessons(asset="AAPL")
        assert len(aapl) == 1

    def test_save_and_load_patterns(self, tmp_path):
        kb = KnowledgeBase(base_path=str(tmp_path))
        patterns = {"AAPL": {"avg_win": 0.05, "best_hours": "10-12"}}
        kb.save_patterns(patterns)
        loaded = kb.load_patterns()
        assert loaded["AAPL"]["best_hours"] == "10-12"
