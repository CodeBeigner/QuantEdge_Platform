package com.QuantPlatformApplication.QuantPlatformApplication.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Fetches ML-relevant market features from Binance public REST APIs.
 * All endpoints are unauthenticated — no API key required.
 *
 * Data points collected:
 *   - Funding rate (Futures)
 *   - Open interest (Futures)
 *   - Order book depth (Futures)
 *   - Spot price (Spot API, for basis calculation)
 *   - Taker buy/sell volume ratio (Futures)
 *   - Global long/short account ratio (Futures)
 */
@Slf4j
@Component
public class BinanceMarketDataClient {

    private static final String FUTURES_BASE_URL = "https://fapi.binance.com";
    private static final String SPOT_BASE_URL = "https://api.binance.com";
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final WebClient futuresClient;
    private final WebClient spotClient;

    public BinanceMarketDataClient() {
        this.futuresClient = WebClient.builder()
                .baseUrl(FUTURES_BASE_URL)
                .codecs(c -> c.defaultCodecs().maxInMemorySize(2 * 1024 * 1024))
                .build();
        this.spotClient = WebClient.builder()
                .baseUrl(SPOT_BASE_URL)
                .codecs(c -> c.defaultCodecs().maxInMemorySize(2 * 1024 * 1024))
                .build();
    }

    /**
     * Fetch current funding rate.
     * GET https://fapi.binance.com/fapi/v1/premiumIndex?symbol=BTCUSDT
     * Returns: { "lastFundingRate": "0.00010000", "nextFundingTime": ..., "markPrice": ... }
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getFundingRate(String symbol) {
        try {
            return futuresClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/fapi/v1/premiumIndex")
                            .queryParam("symbol", symbol)
                            .build())
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(TIMEOUT)
                    .block();
        } catch (Exception e) {
            log.warn("Failed to fetch funding rate for {}: {}", symbol, e.getMessage());
            return Collections.emptyMap();
        }
    }

    /**
     * Fetch open interest.
     * GET https://fapi.binance.com/fapi/v1/openInterest?symbol=BTCUSDT
     * Returns: { "openInterest": "12345.678", "symbol": "BTCUSDT", "time": ... }
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getOpenInterest(String symbol) {
        try {
            return futuresClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/fapi/v1/openInterest")
                            .queryParam("symbol", symbol)
                            .build())
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(TIMEOUT)
                    .block();
        } catch (Exception e) {
            log.warn("Failed to fetch open interest for {}: {}", symbol, e.getMessage());
            return Collections.emptyMap();
        }
    }

    /**
     * Fetch order book depth (top N levels).
     * GET https://fapi.binance.com/fapi/v1/depth?symbol=BTCUSDT&limit=10
     * Returns: { "bids": [[price, qty], ...], "asks": [[price, qty], ...] }
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getOrderBookDepth(String symbol, int limit) {
        try {
            return futuresClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/fapi/v1/depth")
                            .queryParam("symbol", symbol)
                            .queryParam("limit", limit)
                            .build())
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(TIMEOUT)
                    .block();
        } catch (Exception e) {
            log.warn("Failed to fetch order book for {}: {}", symbol, e.getMessage());
            return Collections.emptyMap();
        }
    }

    /**
     * Fetch spot price for basis calculation.
     * GET https://api.binance.com/api/v3/ticker/price?symbol=BTCUSDT
     * Returns: { "symbol": "BTCUSDT", "price": "67000.00" }
     */
    public double getSpotPrice(String symbol) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> result = spotClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/v3/ticker/price")
                            .queryParam("symbol", symbol)
                            .build())
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(TIMEOUT)
                    .block();
            if (result != null && result.containsKey("price")) {
                return Double.parseDouble(result.get("price").toString());
            }
        } catch (Exception e) {
            log.warn("Failed to fetch spot price for {}: {}", symbol, e.getMessage());
        }
        return 0.0;
    }

    /**
     * Fetch taker buy/sell volume.
     * GET https://fapi.binance.com/futures/data/takerlongshortRatio?symbol=BTCUSDT&period=1h&limit=1
     * Returns: [{ "buySellRatio": "1.234", "buyVol": "...", "sellVol": "...", ... }]
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getTakerBuySellVolume(String symbol) {
        try {
            List<Map<String, Object>> result = futuresClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/futures/data/takerlongshortRatio")
                            .queryParam("symbol", symbol)
                            .queryParam("period", "1h")
                            .queryParam("limit", 1)
                            .build())
                    .retrieve()
                    .bodyToMono(List.class)
                    .timeout(TIMEOUT)
                    .block();
            if (result != null && !result.isEmpty()) {
                return result.get(0);
            }
        } catch (Exception e) {
            log.warn("Failed to fetch taker buy/sell volume for {}: {}", symbol, e.getMessage());
        }
        return Collections.emptyMap();
    }

    /**
     * Fetch long/short ratio.
     * GET https://fapi.binance.com/futures/data/globalLongShortAccountRatio?symbol=BTCUSDT&period=1h&limit=1
     * Returns: [{ "longShortRatio": "1.234", "longAccount": "0.55", "shortAccount": "0.45", ... }]
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getLongShortRatio(String symbol) {
        try {
            List<Map<String, Object>> result = futuresClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/futures/data/globalLongShortAccountRatio")
                            .queryParam("symbol", symbol)
                            .queryParam("period", "1h")
                            .queryParam("limit", 1)
                            .build())
                    .retrieve()
                    .bodyToMono(List.class)
                    .timeout(TIMEOUT)
                    .block();
            if (result != null && !result.isEmpty()) {
                return result.get(0);
            }
        } catch (Exception e) {
            log.warn("Failed to fetch long/short ratio for {}: {}", symbol, e.getMessage());
        }
        return Collections.emptyMap();
    }
}
