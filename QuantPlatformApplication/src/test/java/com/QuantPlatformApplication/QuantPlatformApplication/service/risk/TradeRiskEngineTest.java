package com.QuantPlatformApplication.QuantPlatformApplication.service.risk;

import com.QuantPlatformApplication.QuantPlatformApplication.engine.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class TradeRiskEngineTest {

    private TradeRiskEngine engine;
    private RiskParameters defaultParams;

    @BeforeEach
    void setUp() {
        engine = new TradeRiskEngine();
        defaultParams = RiskParameters.builder().build();
    }

    /**
     * Valid long: entry=67000, stop=66650 (distance=350, 0.52%), TP=67525 (reward=525, RR=1.5).
     * With $500 balance at 1% risk: riskAmount=$5, positionSize=5/350=0.01429,
     * notional=957.14, fees=0.957, feeImpact=19.1% (under 20%), effLev=1.91x (under 5x).
     */
    private TradeRequest.TradeRequestBuilder validLongRequest() {
        return TradeRequest.builder()
            .symbol("BTCUSD")
            .action(Action.BUY)
            .entryPrice(67000)
            .stopLossPrice(66650)
            .takeProfitPrice(67525)
            .confidence(0.8)
            .strategyName("TREND_CONTINUATION")
            .reasoning("test");
    }

    @Test
    void approvedTradeHasCorrectPositionSize() {
        TradeRequest req = validLongRequest().build();
        RiskCheckResult result = engine.evaluate(req, 500, 500, 0, 0, Set.of(), defaultParams);
        assertTrue(result.isApproved());
        // riskAmount = 500 * 0.01 = 5.0, stopDistance = 350
        double expectedSize = 5.0 / 350.0;
        assertEquals(expectedSize, result.getPositionSize(), 0.0001);
    }

    @Test
    void rejectsWhenEffectiveLeverageExceedsMax() {
        TradeRequest req = validLongRequest().build();
        // existing exposure 2000 + ~957 notional = ~2957, effLev = 5.9x > 5x
        RiskCheckResult result = engine.evaluate(req, 500, 500, 2000, 0, Set.of(), defaultParams);
        assertFalse(result.isApproved());
        assertTrue(result.getRejectionReasons().stream()
                .anyMatch(r -> r.toLowerCase().contains("leverage")));
    }

    @Test
    void approvesWhenEffectiveLeverageWithinLimit() {
        TradeRequest req = validLongRequest().build();
        // existing exposure 500 + ~957 notional = ~1457, effLev = 2.9x <= 5x
        RiskCheckResult result = engine.evaluate(req, 500, 500, 500, 0, Set.of(), defaultParams);
        assertTrue(result.isApproved());
        assertTrue(result.getEffectiveLeverage() <= 5.0);
    }

    @Test
    void rejectsWhenDailyLossLimitHit() {
        TradeRequest req = validLongRequest().build();
        // dailyLossLimit = 500 * 0.05 = 25; dailyRealizedLoss = 25 >= 25 => rejected
        RiskCheckResult result = engine.evaluate(req, 475, 500, 0, 25, Set.of(), defaultParams);
        assertFalse(result.isApproved());
        assertTrue(result.getRejectionReasons().stream()
                .anyMatch(r -> r.toLowerCase().contains("daily loss")));
    }

    @Test
    void approvesWhenDailyLossUnderLimit() {
        TradeRequest req = validLongRequest().build();
        // dailyLossLimit = 500 * 0.05 = 25; dailyRealizedLoss = 10 < 25 => ok
        RiskCheckResult result = engine.evaluate(req, 490, 500, 0, 10, Set.of(), defaultParams);
        assertTrue(result.isApproved());
    }

    @Test
    void rejectsWhenMaxDrawdownBreached() {
        TradeRequest req = validLongRequest().build();
        // drawdown = (600 - 500) / 600 = 16.7% >= 15% max
        RiskCheckResult result = engine.evaluate(req, 500, 600, 0, 0, Set.of(), defaultParams);
        assertFalse(result.isApproved());
        assertTrue(result.getRejectionReasons().stream()
                .anyMatch(r -> r.toLowerCase().contains("drawdown")));
    }

    @Test
    void rejectsWhenMaxConcurrentPositionsReached() {
        TradeRequest req = validLongRequest().build();
        RiskCheckResult result = engine.evaluate(req, 500, 500, 0, 0,
                Set.of("ETHUSD", "SOLUSD", "XRPUSD"), defaultParams);
        assertFalse(result.isApproved());
        assertTrue(result.getRejectionReasons().stream()
                .anyMatch(r -> r.toLowerCase().contains("concurrent")));
    }

    @Test
    void rejectsDuplicateSymbol() {
        TradeRequest req = validLongRequest().build();
        RiskCheckResult result = engine.evaluate(req, 500, 500, 0, 0, Set.of("BTCUSD"), defaultParams);
        assertFalse(result.isApproved());
        assertTrue(result.getRejectionReasons().stream()
                .anyMatch(r -> r.toLowerCase().contains("already")));
    }

    @Test
    void rejectsMissingStopLoss() {
        TradeRequest req = validLongRequest().stopLossPrice(0).build();
        RiskCheckResult result = engine.evaluate(req, 500, 500, 0, 0, Set.of(), defaultParams);
        assertFalse(result.isApproved());
        assertTrue(result.getRejectionReasons().stream()
                .anyMatch(r -> r.toLowerCase().contains("stop")));
    }

    @Test
    void rejectsStopTooWide() {
        // stopDistance = 67000 - 64990 = 2010, stopPct = 3.0% > 2% max
        TradeRequest req = validLongRequest().stopLossPrice(64990).build();
        RiskCheckResult result = engine.evaluate(req, 500, 500, 0, 0, Set.of(), defaultParams);
        assertFalse(result.isApproved());
        assertTrue(result.getRejectionReasons().stream()
                .anyMatch(r -> r.toLowerCase().contains("stop")));
    }

    @Test
    void rejectsWrongDirectionStop() {
        // BUY with stop above entry => invalid
        TradeRequest req = validLongRequest().stopLossPrice(68000).build();
        RiskCheckResult result = engine.evaluate(req, 500, 500, 0, 0, Set.of(), defaultParams);
        assertFalse(result.isApproved());
        assertTrue(result.getRejectionReasons().stream()
                .anyMatch(r -> r.toLowerCase().contains("stop")));
    }

    @Test
    void rejectsWhenFeeEatsRisk() {
        // stopDistance = 67000 - 66990 = 10, very tight stop
        // positionSize = 5/10 = 0.5, notional = 33500, fees = 33.5
        // feeImpact = 33.5/5 = 670% >> 20%
        TradeRequest req = validLongRequest().stopLossPrice(66990).build();
        RiskCheckResult result = engine.evaluate(req, 500, 500, 0, 0, Set.of(), defaultParams);
        assertFalse(result.isApproved());
        assertTrue(result.getRejectionReasons().stream()
                .anyMatch(r -> r.toLowerCase().contains("fee")));
    }

    @Test
    void rejectsBadRiskReward() {
        // rewardDistance = 67200 - 67000 = 200, stopDistance = 350
        // rrRatio = 200/350 = 0.57 < 1.5 min
        TradeRequest req = validLongRequest().takeProfitPrice(67200).build();
        RiskCheckResult result = engine.evaluate(req, 500, 500, 0, 0, Set.of(), defaultParams);
        assertFalse(result.isApproved());
        assertTrue(result.getRejectionReasons().stream()
                .anyMatch(r -> r.toLowerCase().contains("reward")));
    }

    @Test
    void approvesValidShortTrade() {
        // entry=67000, stop=67350 (distance=350, 0.52%), TP=66100 (reward=900, RR=2.57)
        TradeRequest req = TradeRequest.builder()
            .symbol("BTCUSD")
            .action(Action.SELL)
            .entryPrice(67000)
            .stopLossPrice(67350)
            .takeProfitPrice(66100)
            .confidence(0.8)
            .strategyName("MEAN_REVERSION")
            .reasoning("test")
            .build();
        RiskCheckResult result = engine.evaluate(req, 500, 500, 0, 0, Set.of(), defaultParams);
        assertTrue(result.isApproved());
    }

    @Test
    void fullApprovalReturnsCorrectMetrics() {
        TradeRequest req = validLongRequest().build();
        RiskCheckResult result = engine.evaluate(req, 500, 500, 0, 0, Set.of(), defaultParams);
        assertTrue(result.isApproved());
        assertTrue(result.getPositionSize() > 0);
        assertTrue(result.getRiskAmount() > 0);
        assertTrue(result.getRiskAmount() <= 500 * 0.01 + 0.01);
        assertTrue(result.getEffectiveLeverage() <= 5.0);
        assertTrue(result.getNominalLeverage() >= 10);
    }
}
