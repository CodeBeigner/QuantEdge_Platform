"""Tests for slippage guard — price deviation check before order submission."""
import pytest
from services.execution.slippage import check_slippage


class TestSlippageGuard:
    def test_within_threshold_passes(self):
        result = check_slippage(signal_price=150.0, current_price=151.0, threshold=0.02)
        assert result.ok
        assert result.deviation < 0.02

    def test_exceeds_threshold_aborts(self):
        result = check_slippage(signal_price=100.0, current_price=103.0, threshold=0.02)
        assert not result.ok
        assert result.deviation > 0.02

    def test_exact_threshold_passes(self):
        result = check_slippage(signal_price=100.0, current_price=102.0, threshold=0.02)
        assert result.ok

    def test_price_improvement_always_passes(self):
        result = check_slippage(signal_price=150.0, current_price=148.0, threshold=0.02)
        assert result.ok

    def test_extreme_slippage_short(self):
        result = check_slippage(signal_price=50.0, current_price=45.0, threshold=0.02)
        assert not result.ok
