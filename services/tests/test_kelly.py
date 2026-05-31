"""Tests for Kelly Criterion position sizing."""
import pytest
from services.risk.kelly import kelly_fraction, apply_kelly_fraction


class TestKellyFraction:
    def test_even_odds(self):
        """With even odds (b=1, p=0.5), Kelly says bet nothing."""
        result = kelly_fraction(win_probability=0.5, win_loss_ratio=1.0)
        assert result == pytest.approx(0.0)

    def test_strong_edge(self):
        """60% win rate, 1:1 payouts -> f* = 0.20."""
        result = kelly_fraction(win_probability=0.60, win_loss_ratio=1.0)
        assert result == pytest.approx(0.20)

    def test_high_payout(self):
        """50% win rate, 3:1 payouts -> f* = 0.333."""
        result = kelly_fraction(win_probability=0.50, win_loss_ratio=3.0)
        assert result == pytest.approx(0.33333, rel=1e-3)

    def test_weak_edge(self):
        """51% win rate, 2:1 -> f* = 0.265."""
        result = kelly_fraction(win_probability=0.51, win_loss_ratio=2.0)
        assert result == pytest.approx(0.265)

    def test_guaranteed_win(self):
        """100% win probability -> f*=1 (bet everything)."""
        result = kelly_fraction(win_probability=1.0, win_loss_ratio=1.0)
        assert result == pytest.approx(1.0)

    def test_negative_edge_returns_zero(self):
        """30% win rate -> negative Kelly, should return 0 not negative."""
        result = kelly_fraction(win_probability=0.30, win_loss_ratio=1.0)
        assert result >= 0.0


class TestApplyKellyFraction:
    def test_quarter_kelly(self):
        size = apply_kelly_fraction(p=0.60, b=1.0, nav=100_000.0, fraction=0.25)
        assert size == pytest.approx(5_000.0)

    def test_half_kelly(self):
        size = apply_kelly_fraction(p=0.65, b=2.0, nav=50_000.0, fraction=0.5)
        assert size == pytest.approx(11875.0)

    def test_zero_nav(self):
        size = apply_kelly_fraction(p=0.60, b=1.0, nav=0.0, fraction=0.25)
        assert size == 0.0

    def test_negative_edge_zero_size(self):
        size = apply_kelly_fraction(p=0.30, b=1.0, nav=100_000.0, fraction=0.25)
        assert size == 0.0
