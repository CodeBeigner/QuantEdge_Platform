package com.QuantPlatformApplication.QuantPlatformApplication.client;

import com.QuantPlatformApplication.QuantPlatformApplication.engine.model.Candle;
import com.QuantPlatformApplication.QuantPlatformApplication.engine.model.TimeFrame;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/**
 * Fetches historical OHLCV candle data from Binance Futures public API.
 * No authentication required. Rate limit: ~1200 requests/min.
 *
 * Endpoint: GET https://fapi.binance.com/fapi/v1/klines
 * Returns array of arrays: [openTime, open, high, low, close, volume, closeTime, ...]
 */
@Slf4j
@Component
public class BinanceHistoricalClient {

    private static final String BASE_URL = "https://fapi.binance.com";
    private static final int MAX_CANDLES_PER_REQUEST = 1000;
    private static final int REQUEST_DELAY_MS = 200; // be nice to Binance

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public BinanceHistoricalClient() {
        this.webClient = WebClient.builder()
            .baseUrl(BASE_URL)
            .codecs(c -> c.defaultCodecs().maxInMemorySize(5 * 1024 * 1024))
            .build();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Fetch historical candles for a symbol and timeframe.
     * Automatically paginates to fetch the full date range.
     *
     * @param symbol   Binance symbol (e.g., "BTCUSDT", "ETHUSDT")
     * @param interval Binance interval string ("15m", "1h", "4h")
     * @param from     Start date (inclusive)
     * @param to       End date (inclusive)
     * @return List of Candle objects, chronologically ordered
     */
    public List<Candle> fetchCandles(String symbol, String interval, LocalDate from, LocalDate to) {
        TimeFrame tf = mapInterval(interval);
        long startMs = from.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
        long endMs = to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
        long intervalMs = tf.getMinutes() * 60_000L;

        List<Candle> allCandles = new ArrayList<>();
        long cursor = startMs;

        log.info("Fetching {} {} candles for {} from {} to {}", interval, symbol, interval, from, to);

        while (cursor < endMs) {
            final long cursorFinal = cursor;
            try {
                String response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                        .path("/fapi/v1/klines")
                        .queryParam("symbol", symbol)
                        .queryParam("interval", interval)
                        .queryParam("startTime", cursorFinal)
                        .queryParam("endTime", endMs)
                        .queryParam("limit", MAX_CANDLES_PER_REQUEST)
                        .build())
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(15))
                    .block();

                if (response == null || response.equals("[]")) {
                    break;
                }

                JsonNode rows = objectMapper.readTree(response);
                if (!rows.isArray() || rows.isEmpty()) {
                    break;
                }

                for (JsonNode row : rows) {
                    long openTime = row.get(0).asLong();
                    double open = row.get(1).asDouble();
                    double high = row.get(2).asDouble();
                    double low = row.get(3).asDouble();
                    double close = row.get(4).asDouble();
                    double volume = row.get(5).asDouble();

                    allCandles.add(new Candle(
                        Instant.ofEpochMilli(openTime),
                        open, high, low, close, volume, tf
                    ));
                }

                // Move cursor past last candle
                long lastTime = rows.get(rows.size() - 1).get(0).asLong();
                cursor = lastTime + intervalMs;

                log.debug("Fetched {} candles, total so far: {}, cursor at {}",
                    rows.size(), allCandles.size(), Instant.ofEpochMilli(cursor));

                // Rate limit courtesy
                if (cursor < endMs) {
                    Thread.sleep(REQUEST_DELAY_MS);
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("Failed to fetch candles at cursor {}: {}", cursor, e.getMessage());
                break;
            }
        }

        log.info("Fetched {} total {} candles for {}", allCandles.size(), interval, symbol);
        return allCandles;
    }

    /**
     * Convenience: fetch 15m candles for backtesting (most common use case).
     */
    public List<Candle> fetch15mCandles(String symbol, LocalDate from, LocalDate to) {
        return fetchCandles(symbol, "15m", from, to);
    }

    /**
     * Map Binance symbol from our internal format.
     * Our system uses "BTCUSD" but Binance uses "BTCUSDT".
     */
    public static String toBinanceSymbol(String symbol) {
        return switch (symbol.toUpperCase()) {
            case "BTCUSD", "BTCUSDT" -> "BTCUSDT";
            case "ETHUSD", "ETHUSDT" -> "ETHUSDT";
            case "SOLUSD", "SOLUSDT" -> "SOLUSDT";
            case "XRPUSD", "XRPUSDT" -> "XRPUSDT";
            default -> symbol.toUpperCase();
        };
    }

    private TimeFrame mapInterval(String interval) {
        return switch (interval) {
            case "15m" -> TimeFrame.M15;
            case "1h" -> TimeFrame.H1;
            case "4h" -> TimeFrame.H4;
            default -> TimeFrame.M15;
        };
    }
}
