package com.QuantPlatformApplication.QuantPlatformApplication.engine.model;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * Market data snapshot used as input for strategy execution.
 * Contains price history, macro indicators, and volatility data.
 */
@Getter
@Setter
public class MarketData {

    private List<Double> prices = List.of();
    private List<Double> secondaryPrices = List.of(); // for correlation strategy
    private double currentPrice;
    private double interestRate = 3.0;
    private double inflationRate = 2.5;
    private double vix = 15.0;
    private double yieldCurveSpread = 0.5; // 10Y − 2Y
}
