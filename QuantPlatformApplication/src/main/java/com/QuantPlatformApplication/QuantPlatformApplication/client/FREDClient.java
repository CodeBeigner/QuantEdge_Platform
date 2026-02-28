package com.QuantPlatformApplication.QuantPlatformApplication.client;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * FRED (Federal Reserve Economic Data) API client.

 * Fetches macroeconomic indicators:
 * - Federal Funds Rate (DFF)
 * - Consumer Price Index / Inflation (CPIAUCSL)
 * - 10-Year Treasury Yield (DGS10)
 * - 2-Year Treasury Yield (DGS2)

 * Uses the FRED observations API with JSON format.
 */
@Slf4j
@Service
public class FREDClient {

    private final WebClient webClient;
    private final String apiKey;

    public FREDClient(
            @Value("${fred.base-url:https://api.stlouisfed.org}") String baseUrl,
            @Value("${fred.api-key:DEMO_KEY}") String apiKey) {
        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .build();
        this.apiKey = apiKey;
    }

    /**
     * Fetch the latest value for a FRED series.
     *
     * @param seriesId FRED series ID (e.g., "DFF", "CPIAUCSL")
     * @return Latest observation value, or default if unavailable
     */
    @SuppressWarnings("unchecked")
    public double fetchLatestValue(String seriesId, double defaultValue) {
        try {
            Map<String, Object> response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/fred/series/observations")
                            .queryParam("series_id", seriesId)
                            .queryParam("api_key", apiKey)
                            .queryParam("file_type", "json")
                            .queryParam("sort_order", "desc")
                            .queryParam("limit", "1")
                            .build())
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofSeconds(10))
                    .retryWhen(Retry.backoff(3, Duration.ofSeconds(1)))
                    .block();

            if (response == null)
                return defaultValue;

            List<Map<String, String>> observations = (List<Map<String, String>>) response.get("observations");

            if (observations == null || observations.isEmpty())
                return defaultValue;

            String value = observations.get(0).get("value");
            if (value == null || value.equals("."))
                return defaultValue;

            double result = Double.parseDouble(value);
            log.info("FRED {} = {}", seriesId, result);
            return result;

        } catch (Exception e) {
            log.warn("Failed to fetch FRED series {}: {} — using default {}", seriesId, e.getMessage(), defaultValue);
            return defaultValue;
        }
    }

    /** Get current Federal Funds rate */
    public double getFedFundsRate() {
        return fetchLatestValue("DFF", 5.33);
    }

    /** Get latest CPI year-over-year (approximated via level) */
    public double getInflationRate() {
        return fetchLatestValue("CPIAUCSL", 3.0);
    }

    /** Get 10-Year Treasury yield */
    public double get10YearYield() {
        return fetchLatestValue("DGS10", 4.5);
    }

    /** Get 2-Year Treasury yield */
    public double get2YearYield() {
        return fetchLatestValue("DGS2", 4.2);
    }

    /**
     * Compute the yield curve spread (10Y - 2Y).
     * Negative = inverted yield curve (recession signal).
     */
    public double getYieldCurveSpread() {
        return get10YearYield() - get2YearYield();
    }

    /** Bundle all macro indicators into one object */
    public MacroIndicators getMacroIndicators() {
        return new MacroIndicators(
                getFedFundsRate(),
                getInflationRate(),
                get10YearYield(),
                get2YearYield(),
                getYieldCurveSpread());
    }

    @Getter
    public static class MacroIndicators {
        private final double fedFundsRate;
        private final double inflationRate;
        private final double yield10Y;
        private final double yield2Y;
        private final double yieldCurveSpread;

        public MacroIndicators(double fedFundsRate, double inflationRate,
                double yield10Y, double yield2Y, double yieldCurveSpread) {
            this.fedFundsRate = fedFundsRate;
            this.inflationRate = inflationRate;
            this.yield10Y = yield10Y;
            this.yield2Y = yield2Y;
            this.yieldCurveSpread = yieldCurveSpread;
        }
    }
}
