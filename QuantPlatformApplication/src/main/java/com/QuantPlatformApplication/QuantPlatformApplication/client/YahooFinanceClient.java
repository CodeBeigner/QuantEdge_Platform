package com.QuantPlatformApplication.QuantPlatformApplication.client;

import com.QuantPlatformApplication.QuantPlatformApplication.model.entity.MarketDataEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.util.retry.Retry;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Yahoo Finance API client using Spring WebClient.

 * Fetches historical OHLCV data for any ticker symbol.
 * Uses the Yahoo Finance v8 chart API endpoint.

 * Features:
 * - Non-blocking HTTP via WebClient
 * - Retry (3 attempts) with exponential backoff
 * - 10-second timeout
 * - Graceful error handling (returns empty list on failure)
 */
@Slf4j
@Service
public class YahooFinanceClient {

    private final WebClient webClient;

    public YahooFinanceClient(
            @Value("${yahoo.finance.base-url:https://query1.finance.yahoo.com}") String baseUrl) {
        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("User-Agent", "Mozilla/5.0")
                .build();
    }

    /**
     * Fetch historical daily OHLCV data for a symbol.
     *
     * @param symbol Ticker (e.g., "AAPL", "SPY")
     * @param days   Number of days of history to fetch
     * @return List of MarketDataEntity (empty on error)
     */
    @SuppressWarnings("unchecked")
    public List<MarketDataEntity> fetchHistoricalData(String symbol, int days) {
        try {
            String period1 = String.valueOf(
                    Instant.now().minus(Duration.ofDays(days)).getEpochSecond());
            String period2 = String.valueOf(Instant.now().getEpochSecond());

            Map<String, Object> response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v8/finance/chart/{symbol}")
                            .queryParam("period1", period1)
                            .queryParam("period2", period2)
                            .queryParam("interval", "1d")
                            .build(symbol.toUpperCase()))
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofSeconds(10))
                    .retryWhen(Retry.backoff(3, Duration.ofSeconds(1)))
                    .block();

            if (response == null) {
                log.warn("Null response from Yahoo Finance for {}", symbol);
                return List.of();
            }

            return parseYahooResponse(symbol, response);

        } catch (Exception e) {
            log.error("Failed to fetch Yahoo Finance data for {}: {}", symbol, e.getMessage());
            return List.of();
        }
    }

    @SuppressWarnings("unchecked")
    private List<MarketDataEntity> parseYahooResponse(String symbol, Map<String, Object> response) {
        List<MarketDataEntity> entities = new ArrayList<>();
        try {
            Map<String, Object> chart = (Map<String, Object>) response.get("chart");
            List<Map<String, Object>> results = (List<Map<String, Object>>) chart.get("result");

            if (results == null || results.isEmpty())
                return entities;

            Map<String, Object> result = results.get(0);
            List<Number> timestamps = (List<Number>) result.get("timestamp");
            Map<String, Object> indicators = (Map<String, Object>) result.get("indicators");
            List<Map<String, Object>> quotes = (List<Map<String, Object>>) indicators.get("quote");
            Map<String, Object> quote = quotes.get(0);

            List<Number> opens = (List<Number>) quote.get("open");
            List<Number> highs = (List<Number>) quote.get("high");
            List<Number> lows = (List<Number>) quote.get("low");
            List<Number> closes = (List<Number>) quote.get("close");
            List<Number> volumes = (List<Number>) quote.get("volume");

            for (int i = 0; i < timestamps.size(); i++) {
                if (closes.get(i) == null)
                    continue; // skip null entries

                entities.add(MarketDataEntity.builder()
                        .time(Instant.ofEpochSecond(timestamps.get(i).longValue()))
                        .symbol(symbol.toUpperCase())
                        .open(toBigDecimal(opens.get(i)))
                        .high(toBigDecimal(highs.get(i)))
                        .low(toBigDecimal(lows.get(i)))
                        .close(toBigDecimal(closes.get(i)))
                        .volume(volumes.get(i) != null ? volumes.get(i).longValue() : 0L)
                        .build());
            }

            log.info("Fetched {} data points from Yahoo Finance for {}", entities.size(), symbol);
        } catch (Exception e) {
            log.error("Error parsing Yahoo Finance response for {}: {}", symbol, e.getMessage());
        }
        return entities;
    }

    private BigDecimal toBigDecimal(Number n) {
        return n != null ? BigDecimal.valueOf(n.doubleValue()) : BigDecimal.ZERO;
    }
}
