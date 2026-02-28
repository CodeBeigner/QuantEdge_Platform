package com.QuantPlatformApplication.QuantPlatformApplication.service;

import com.QuantPlatformApplication.QuantPlatformApplication.client.YahooFinanceClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/**
 * Combines VIX, yield curve, and inflation into a regime assessment.
 * Used by RegimeStrategy and CorrelationStrategy to enrich MarketData.
 */
@Slf4j
@Service
public class RegimeDetectionService {

    private final MacroDataService macroDataService;
    private final YahooFinanceClient yahooFinanceClient;

    public RegimeDetectionService(MacroDataService macroDataService,
            YahooFinanceClient yahooFinanceClient) {
        this.macroDataService = macroDataService;
        this.yahooFinanceClient = yahooFinanceClient;
    }

    /**
     * Get current VIX level (fear index).
     * Fetches from Yahoo Finance using ^VIX ticker.
     */
    @Cacheable(value = "macroData", key = "'vix'")
    public double getCurrentVix() {
        try {
            var vixData = yahooFinanceClient.fetchHistoricalData("^VIX", 5);
            if (!vixData.isEmpty()) {
                return vixData.getLast().getClose().doubleValue();
            }
        } catch (Exception e) {
            log.warn("Failed to fetch VIX: {}", e.getMessage());
        }
        return 15.0; // default
    }

    /** Get yield curve spread from FRED data */
    public double getYieldCurveSpread() {
        return macroDataService.getYieldCurveSpread();
    }

    /** Get current inflation rate */
    public double getInflationRate() {
        return macroDataService.getInflationRate();
    }

    /** Get current interest rate */
    public double getInterestRate() {
        return macroDataService.getInterestRate();
    }
}
