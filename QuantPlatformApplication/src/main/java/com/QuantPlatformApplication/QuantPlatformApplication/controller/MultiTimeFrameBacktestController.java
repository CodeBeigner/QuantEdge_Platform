package com.QuantPlatformApplication.QuantPlatformApplication.controller;

import com.QuantPlatformApplication.QuantPlatformApplication.client.BinanceHistoricalClient;
import com.QuantPlatformApplication.QuantPlatformApplication.engine.data.CandleSource;
import com.QuantPlatformApplication.QuantPlatformApplication.engine.data.EmptyCandleRangeException;
import com.QuantPlatformApplication.QuantPlatformApplication.engine.model.BacktestConfig;
import com.QuantPlatformApplication.QuantPlatformApplication.engine.model.Candle;
import com.QuantPlatformApplication.QuantPlatformApplication.engine.model.MultiTimeFrameBacktestResult;
import com.QuantPlatformApplication.QuantPlatformApplication.service.MultiTimeFrameBacktestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/backtests/multi-tf")
@RequiredArgsConstructor
public class MultiTimeFrameBacktestController {

    private final MultiTimeFrameBacktestService backtestService;
    private final CandleSource candleSource;
    private final BinanceHistoricalClient binanceClient;

    @PostMapping
    public ResponseEntity<?> runBacktest(@RequestBody Map<String, Object> request) {
        double capital = request.containsKey("initialCapital")
            ? ((Number) request.get("initialCapital")).doubleValue() : 500;
        double slippage = request.containsKey("slippageBps")
            ? ((Number) request.get("slippageBps")).doubleValue() : 5.0;

        String rawSymbol = request.containsKey("symbol") ? (String) request.get("symbol") : "BTCUSDT";
        String symbol = BinanceHistoricalClient.toBinanceSymbol(rawSymbol);
        String timeframe = request.containsKey("timeframe") ? (String) request.get("timeframe") : "15m";

        LocalDate endDate = request.containsKey("endDate")
            ? LocalDate.parse((String) request.get("endDate")) : LocalDate.now();
        LocalDate startDate = request.containsKey("startDate")
            ? LocalDate.parse((String) request.get("startDate")) : endDate.minusMonths(3);

        BacktestConfig.BacktestConfigBuilder cfgBuilder = BacktestConfig.builder()
            .initialCapital(capital)
            .slippageBps(slippage);
        if (request.get("useMetaFilter") instanceof Boolean umf) cfgBuilder.useMetaFilter(umf);
        if (request.get("metaThreshold") instanceof Number mt)   cfgBuilder.metaThreshold(mt.doubleValue());
        if (request.get("metaSymbol") instanceof String ms)      cfgBuilder.metaSymbol(ms);
        BacktestConfig config = cfgBuilder.build();

        List<Candle> candles;
        try {
            candles = candleSource.fetch(symbol, timeframe, startDate, endDate);
        } catch (EmptyCandleRangeException e) {
            log.warn("Empty candle range: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("error", "no_data", "message", e.getMessage()));
        }

        log.info("Running backtest with {} {} candles for {}, ${} capital, {} bps slippage",
            candles.size(), timeframe, symbol, capital, slippage);

        MultiTimeFrameBacktestResult result = backtestService.runBacktest(candles, config);
        return ResponseEntity.ok(result);
    }

    /**
     * Fetch raw candle data for frontend charts. Uses the live Binance REST
     * client because the frontend chart wants recent candles that may not yet
     * be seeded. This is a read-only convenience endpoint, not part of the
     * backtest data path.
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
}
