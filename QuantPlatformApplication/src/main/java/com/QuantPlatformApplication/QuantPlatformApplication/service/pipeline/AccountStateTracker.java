package com.QuantPlatformApplication.QuantPlatformApplication.service.pipeline;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

/**
 * Tracks account state needed by the risk engine.
 * Updated after every trade result and daily reset.
 */
@Slf4j
@Component
public class AccountStateTracker {

    @Getter private double currentBalance;
    @Getter private double peakEquity;
    @Getter private double dayStartBalance;
    @Getter private double dailyRealizedLoss;
    @Getter private double currentExposure;
    @Getter private final Set<String> openPositionSymbols = new HashSet<>();

    public void initialize(double startingBalance) {
        this.currentBalance = startingBalance;
        this.peakEquity = startingBalance;
        this.dayStartBalance = startingBalance;
        this.dailyRealizedLoss = 0;
        this.currentExposure = 0;
        this.openPositionSymbols.clear();
        log.info("Account initialized: balance=${}", startingBalance);
    }

    public void recordTradeResult(String symbol, double pnl) {
        currentBalance += pnl;

        if (pnl < 0) {
            dailyRealizedLoss += Math.abs(pnl);
        }

        if (currentBalance > peakEquity) {
            peakEquity = currentBalance;
        }

        log.info("Trade result for {}: ${} | Balance: ${} | Peak: ${} | Daily loss: ${}",
            symbol, pnl, currentBalance, peakEquity, dailyRealizedLoss);
    }

    public void addPosition(String symbol, double notionalValue) {
        openPositionSymbols.add(symbol);
        currentExposure += notionalValue;
    }

    public void removePosition(String symbol, double notionalValue) {
        openPositionSymbols.remove(symbol);
        currentExposure = Math.max(0, currentExposure - notionalValue);
    }

    public void resetDaily() {
        dailyRealizedLoss = 0;
        dayStartBalance = currentBalance;
        log.info("Daily reset. Starting balance: ${}", dayStartBalance);
    }
}
