package com.QuantPlatformApplication.QuantPlatformApplication.service.pipeline;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

@Slf4j
@Component
public class FundingRateTracker {

    private static final int MAX_HISTORY = 72;  // 9 days of 8h intervals
    private static final double EXTREME_POSITIVE_THRESHOLD = 0.0005; // 0.05%
    private static final double EXTREME_NEGATIVE_THRESHOLD = -0.0003; // -0.03%

    private final Deque<Double> history = new ArrayDeque<>();

    public void recordFundingRate(double rate) {
        history.addFirst(rate);
        while (history.size() > MAX_HISTORY) {
            history.removeLast();
        }
        log.debug("Funding rate recorded: {} (history size: {})", rate, history.size());
    }

    public double getCurrentRate() {
        return history.isEmpty() ? 0 : history.peekFirst();
    }

    public List<Double> getHistory() {
        return new ArrayList<>(history);
    }

    public boolean isExtremePositive(int consecutivePeriods) {
        if (history.size() < consecutivePeriods) return false;
        int count = 0;
        for (Double rate : history) {
            if (rate > EXTREME_POSITIVE_THRESHOLD) {
                count++;
                if (count >= consecutivePeriods) return true;
            } else {
                break; // must be consecutive
            }
        }
        return false;
    }

    public boolean isExtremeNegative(int consecutivePeriods) {
        if (history.size() < consecutivePeriods) return false;
        int count = 0;
        for (Double rate : history) {
            if (rate < EXTREME_NEGATIVE_THRESHOLD) {
                count++;
                if (count >= consecutivePeriods) return true;
            } else {
                break;
            }
        }
        return false;
    }
}
