package com.QuantPlatformApplication.QuantPlatformApplication.controller;

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

    @PostMapping
    public ResponseEntity<MultiTimeFrameBacktestResult> runBacktest(
            @RequestBody Map<String, Object> request) {

        double capital = request.containsKey("initialCapital")
            ? ((Number) request.get("initialCapital")).doubleValue() : 500;
        double slippage = request.containsKey("slippageBps")
            ? ((Number) request.get("slippageBps")).doubleValue() : 10;

        BacktestConfig config = BacktestConfig.builder()
            .initialCapital(capital)
            .slippageBps(slippage)
            .build();

        // Generate realistic 15m candle data for backtesting
        // TODO: Replace with real Delta Exchange historical data when available
        List<Candle> candles = generateSampleCandles(2000);
        log.info("Running backtest with {} sample 15m candles, ${} capital, {} bps slippage",
            candles.size(), capital, slippage);

        MultiTimeFrameBacktestResult result = backtestService.runBacktest(candles, config);
        return ResponseEntity.ok(result);
    }

    /**
     * Generate realistic 15m crypto candle data with trends, mean reversion, and volatility clusters.
     * Uses random walk with drift, volatility clustering, and occasional trend changes.
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
