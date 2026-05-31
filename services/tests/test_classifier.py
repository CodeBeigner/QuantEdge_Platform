"""Tests for Strategy Classifier + Regime Guard."""
import numpy as np
import pytest
from services.classifier.strategy_style import StrategyStyle, AssetClass, StrategyRegistration
from services.classifier.regime_guard import classify_regime, is_adverse_regime, regime_transition_risk
from services.classifier.signal_policy import enforce_policy, validate_registration, allocation_cap


@pytest.fixture
def returns():
    rng = np.random.RandomState(42)
    return rng.normal(0.001, 0.02, 100)


@pytest.fixture
def trend_following_reg():
    return StrategyRegistration(
        name="tf_test",
        style=StrategyStyle.TREND_FOLLOWING,
        asset_class=AssetClass.EQUITY,
        survivorship_bias_corrected=False,
    )


@pytest.fixture
def mean_reversion_reg():
    return StrategyRegistration(
        name="mr_test",
        style=StrategyStyle.MEAN_REVERSION,
        asset_class=AssetClass.CRYPTO,
        regime_attached=True,
    )


@pytest.fixture
def stat_arb_reg():
    return StrategyRegistration(
        name="sa_test",
        style=StrategyStyle.STAT_ARB,
        asset_class=AssetClass.EQUITY,
        correlated_assets=["AAPL", "MSFT"],
    )


@pytest.fixture
def hft_reg():
    return StrategyRegistration(
        name="hft_test",
        style=StrategyStyle.HFT,
        asset_class=AssetClass.FUTURES,
        hft_override_acknowledged=True,
    )


class TestStrategyStyle:
    def test_all_styles_exist(self):
        styles = list(StrategyStyle)
        assert len(styles) == 10


class TestAssetClass:
    def test_all_classes_exist(self):
        classes = list(AssetClass)
        assert len(classes) == 4


class TestRegimeGuard:
    def test_classify_regime(self, returns):
        regime, model = classify_regime(returns)
        assert regime in ["bear", "sideways", "bull"]

    def test_classify_insufficient_data(self):
        regime, model = classify_regime(np.array([0.01, 0.02]))
        assert regime == "sideways"
        assert model is None

    def test_is_adverse_regime_mean_reversion(self):
        assert is_adverse_regime("bull", "MEAN_REVERSION")
        assert is_adverse_regime("bear", "MEAN_REVERSION")
        assert not is_adverse_regime("sideways", "MEAN_REVERSION")

    def test_is_adverse_regime_trend_following(self):
        assert is_adverse_regime("sideways", "TREND_FOLLOWING")
        assert not is_adverse_regime("bull", "TREND_FOLLOWING")

    def test_transition_risk(self, returns):
        risk = regime_transition_risk(returns)
        assert 0.0 <= risk <= 1.0


class TestSignalPolicy:
    def test_mean_reversion_suppressed_in_bull(self, mean_reversion_reg):
        result = enforce_policy(mean_reversion_reg, "bull")
        assert result.suppressed
        assert "suppressed" in result.reason.lower()

    def test_mean_reversion_allowed_in_sideways(self, mean_reversion_reg):
        result = enforce_policy(mean_reversion_reg, "sideways")
        assert not result.suppressed

    def test_mean_reversion_no_regime_attachment_blocked(self):
        reg = StrategyRegistration(name="mr_no_regime", style=StrategyStyle.MEAN_REVERSION, asset_class=AssetClass.EQUITY, regime_attached=False)
        result = enforce_policy(reg, "sideways")
        assert result.suppressed

    def test_stat_arb_no_correlated_assets_blocked(self):
        reg = StrategyRegistration(name="sa_bad", style=StrategyStyle.STAT_ARB, asset_class=AssetClass.EQUITY)
        result = enforce_policy(reg, "sideways")
        assert result.suppressed

    def test_stat_arb_low_correlation_suppressed(self, stat_arb_reg):
        result = enforce_policy(stat_arb_reg, "sideways", current_correlation=0.50)
        assert result.suppressed

    def test_stat_arb_good_correlation_allowed(self, stat_arb_reg):
        result = enforce_policy(stat_arb_reg, "sideways", current_correlation=0.80)
        assert not result.suppressed

    def test_hft_no_override_blocked(self):
        reg = StrategyRegistration(name="hft_bad", style=StrategyStyle.HFT, asset_class=AssetClass.FUTURES, hft_override_acknowledged=False)
        result = enforce_policy(reg, "sideways")
        assert result.suppressed

    def test_hft_with_override_allowed(self, hft_reg):
        result = enforce_policy(hft_reg, "sideways")
        assert not result.suppressed

    def test_trend_following_allowed(self, trend_following_reg):
        result = enforce_policy(trend_following_reg, "bull")
        assert not result.suppressed


class TestValidation:
    def test_trend_following_warns_no_survivorship(self, trend_following_reg):
        warnings = validate_registration(trend_following_reg)
        assert any("survivorship" in w.lower() for w in warnings)

    def test_stat_arb_warns_no_correlated(self):
        reg = StrategyRegistration(name="sa", style=StrategyStyle.STAT_ARB, asset_class=AssetClass.EQUITY)
        warnings = validate_registration(reg)
        assert any("correlated" in w.lower() for w in warnings)

    def test_hft_warns_no_override(self):
        reg = StrategyRegistration(name="hft", style=StrategyStyle.HFT, asset_class=AssetClass.FUTURES)
        warnings = validate_registration(reg)
        assert any("override" in w.lower() for w in warnings)


class TestAllocationCap:
    def test_default_cap(self, trend_following_reg):
        cap = allocation_cap(trend_following_reg, 100_000.0)
        assert cap == 15_000.0

    def test_mean_reversion_capped(self, mean_reversion_reg):
        cap = allocation_cap(mean_reversion_reg, 100_000.0)
        assert cap <= 15_000.0

    def test_hft_capped(self, hft_reg):
        cap = allocation_cap(hft_reg, 100_000.0)
        assert cap <= 10_000.0
