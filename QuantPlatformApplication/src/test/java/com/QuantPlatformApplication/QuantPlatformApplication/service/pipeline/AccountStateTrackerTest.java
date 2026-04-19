package com.QuantPlatformApplication.QuantPlatformApplication.service.pipeline;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AccountStateTrackerTest {

    private AccountStateTracker tracker;

    @BeforeEach
    void setUp() {
        tracker = new AccountStateTracker();
        tracker.initialize(500.0);
    }

    @Test
    void initializeSetsBalanceAndPeakEquity() {
        assertEquals(500.0, tracker.getCurrentBalance());
        assertEquals(500.0, tracker.getPeakEquity());
        assertEquals(500.0, tracker.getDayStartBalance());
        assertEquals(0.0, tracker.getDailyRealizedLoss());
    }

    @Test
    void recordWinUpdatesBalanceAndPeakEquity() {
        tracker.recordTradeResult("BTCUSD", 12.50);
        assertEquals(512.50, tracker.getCurrentBalance());
        assertEquals(512.50, tracker.getPeakEquity());
    }

    @Test
    void recordLossUpdatesBalanceAndDailyLoss() {
        tracker.recordTradeResult("BTCUSD", -8.0);
        assertEquals(492.0, tracker.getCurrentBalance());
        assertEquals(500.0, tracker.getPeakEquity()); // peak unchanged
        assertEquals(8.0, tracker.getDailyRealizedLoss());
    }

    @Test
    void peakEquityOnlyIncreasesNeverDecreases() {
        tracker.recordTradeResult("BTCUSD", 20.0);  // equity = 520
        tracker.recordTradeResult("ETHUSD", -10.0);  // equity = 510
        assertEquals(520.0, tracker.getPeakEquity()); // peak stays at 520
        assertEquals(510.0, tracker.getCurrentBalance());
    }

    @Test
    void resetDailyStatsClearsLossAndUpdatesStart() {
        tracker.recordTradeResult("BTCUSD", -15.0);
        assertEquals(15.0, tracker.getDailyRealizedLoss());

        tracker.resetDaily();
        assertEquals(0.0, tracker.getDailyRealizedLoss());
        assertEquals(485.0, tracker.getDayStartBalance());
    }
}
