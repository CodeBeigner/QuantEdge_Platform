package com.QuantPlatformApplication.QuantPlatformApplication.service;

import com.QuantPlatformApplication.QuantPlatformApplication.client.FREDClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/**
 * Service for fetching macroeconomic data used by Macro and Regime strategies.
 * Wraps the FREDClient and adds caching.
 */
@Slf4j
@Service
public class MacroDataService {

    private final FREDClient fredClient;

    public MacroDataService(FREDClient fredClient) {
        this.fredClient = fredClient;
    }

    @Cacheable(value = "macroData", key = "'interestRate'")
    public double getInterestRate() {
        return fredClient.getFedFundsRate();
    }

    @Cacheable(value = "macroData", key = "'inflationRate'")
    public double getInflationRate() {
        return fredClient.getInflationRate();
    }

    @Cacheable(value = "macroData", key = "'yieldCurveSpread'")
    public double getYieldCurveSpread() {
        return fredClient.getYieldCurveSpread();
    }

    @Cacheable(value = "macroData", key = "'yield10Y'")
    public double get10YearYield() {
        return fredClient.get10YearYield();
    }
}
