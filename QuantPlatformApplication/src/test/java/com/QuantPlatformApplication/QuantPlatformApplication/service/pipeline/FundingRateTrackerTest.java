package com.QuantPlatformApplication.QuantPlatformApplication.service.pipeline;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FundingRateTrackerTest {

    private FundingRateTracker tracker;

    @BeforeEach
    void setUp() {
        tracker = new FundingRateTracker();
    }

    @Test
    void recordsAndReturnsHistory() {
        tracker.recordFundingRate(0.01);
        tracker.recordFundingRate(0.02);
        tracker.recordFundingRate(0.015);

        List<Double> history = tracker.getHistory();
        assertEquals(3, history.size());
        assertEquals(0.015, history.get(0)); // newest first
    }

    @Test
    void historyLimitedToMaxSize() {
        for (int i = 0; i < 100; i++) {
            tracker.recordFundingRate(i * 0.001);
        }
        assertTrue(tracker.getHistory().size() <= 72); // max 72 (9 days of 8h intervals)
    }

    @Test
    void currentRateReturnsLatest() {
        tracker.recordFundingRate(0.01);
        tracker.recordFundingRate(0.05);
        assertEquals(0.05, tracker.getCurrentRate());
    }

    @Test
    void isExtremeDetectsHighPositive() {
        tracker.recordFundingRate(0.06);
        tracker.recordFundingRate(0.07);
        tracker.recordFundingRate(0.065);

        assertTrue(tracker.isExtremePositive(3));
    }
}
