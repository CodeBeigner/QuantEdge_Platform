package com.QuantPlatformApplication.QuantPlatformApplication.controller;

import com.QuantPlatformApplication.QuantPlatformApplication.client.BinanceHistoricalClient;
import com.QuantPlatformApplication.QuantPlatformApplication.engine.model.BacktestConfig;
import com.QuantPlatformApplication.QuantPlatformApplication.engine.model.Candle;
import com.QuantPlatformApplication.QuantPlatformApplication.engine.model.MultiTimeFrameBacktestResult;
import com.QuantPlatformApplication.QuantPlatformApplication.engine.model.TimeFrame;
import com.QuantPlatformApplication.QuantPlatformApplication.service.MultiTimeFrameBacktestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

@Slf4j
@RestController
@RequestMapping("/api/v1/backtests/multi-tf")
@RequiredArgsConstructor
public class MultiTimeFrameBacktestController {

    private final MultiTimeFrameBacktestService backtestService;
    private final BinanceHistoricalClient binanceClient;

    @PostMapping
    public ResponseEntity<MultiTimeFrameBacktestResult> runBacktest(
            @RequestBody Map<String, Object> request) {

        double capital = request.containsKey("initialCapital")
            ? ((Number) request.get("initialCapital")).doubleValue() : 500;
        double slippage = request.containsKey("slippageBps")
            ? ((Number) request.get("slippageBps")).doubleValue() : 10;

        String symbol = request.containsKey("symbol")
            ? (String) request.get("symbol") : "BTCUSDT";
        symbol = BinanceHistoricalClient.toBinanceSymbol(symbol);

        LocalDate endDate = request.containsKey("endDate")
            ? LocalDate.parse((String) request.get("endDate"))
            : LocalDate.now();
        LocalDate startDate = request.containsKey("startDate")
            ? LocalDate.parse((String) request.get("startDate"))
            : endDate.minusMonths(3);

        BacktestConfig config = BacktestConfig.builder()
            .initialCapital(capital)
            .slippageBps(slippage)
            .build();

        // Fetch real candles from Binance Futures
        List<Candle> candles;
        try {
            candles = binanceClient.fetch15mCandles(symbol, startDate, endDate);
        } catch (Exception e) {
            log.warn("Binance fetch failed for {}: {}, falling back to sample data", symbol, e.getMessage());
            candles = List.of();
        }

        // Fall back to sample data if Binance is unreachable or returned nothing
        if (candles.isEmpty()) {
            log.info("Using generated sample candles as fallback");
            candles = generateSampleCandles(2000);
        }

        log.info("Running backtest with {} 15m candles for {}, ${} capital, {} bps slippage",
            candles.size(), symbol, capital, slippage);

        MultiTimeFrameBacktestResult result = backtestService.runBacktest(candles, config);
        return ResponseEntity.ok(result);
    }

    /**
     * Fetch raw candle data for frontend charts.
     * GET /api/v1/backtests/multi-tf/candles?symbol=BTCUSDT&interval=15m&days=7
     */
    @GetMapping("/candles")
    public ResponseEntity<List<Map<String, Object>>> getCandles(
            @RequestParam(defaultValue = "BTCUSDT") String symbol,
            @RequestParam(defaultValue = "15m") String interval,
            @RequestParam(defaultValue = "7") int days) {

        LocalDate to = LocalDate.now();
        LocalDate from = to.minusDays(days);

        List<Candle> candles = binanceClient.fetchCandles(
            BinanceHistoricalClient.toBinanceSymbol(symbol), interval, from, to);

        List<Map<String, Object>> result = candles.stream()
            .map(c -> Map.<String, Object>of(
                "time", c.timestamp().getEpochSecond(),
                "open", c.open(),
                "high", c.high(),
                "low", c.low(),
                "close", c.close(),
                "volume", c.volume()
            ))
            .toList();

        return ResponseEntity.ok(result);
    }

    /**
     * Generate realistic 15m crypto candle data with trends, mean reversion, and volatility clusters.
     * Uses random walk with drift, volatility clustering, and occasional trend changes.
     * Kept as fallback when Binance API is unreachable.
     */
    private List<Candle> generateSampleCandles(int count) {
        List<Candle> candles = new ArrayList<>();
        Random rng = new Random(42); // fixed seed for reproducibility
        Instant base = Instant.parse("2025-06-01T00:00:00Z");

        double price = 67000;
        double volatility = 0.003; // 0.3% per 15m candle
        double drift = 0.00005;    // slight upward bias

        for (int i = 0; i < count; i++) {
            // Volatility clustering: volatility reverts to mean with random shocks
            volatility = 0.7 * volatility + 0.3 * 0.003 + rng.nextGaussian() * 0.0005;
            volatility = Math.max(0.001, Math.min(0.01, volatility));

            // Trend changes every ~200 candles
            if (i % 200 == 0) {
                drift = (rng.nextDouble() - 0.45) * 0.0003;
            }

            double change = drift + rng.nextGaussian() * volatility;
            double open = price;
            double close = price * (1 + change);
            double high = Math.max(open, close) * (1 + Math.abs(rng.nextGaussian()) * volatility * 0.5);
            double low = Math.min(open, close) * (1 - Math.abs(rng.nextGaussian()) * volatility * 0.5);
            double volume = 500 + rng.nextDouble() * 2000 + (Math.abs(change) > 0.005 ? 3000 : 0);

            candles.add(new Candle(
                base.plusSeconds(i * 900L),
                open, high, low, close, volume, TimeFrame.M15
            ));

            price = close;
        }

        return candles;
    }
}
