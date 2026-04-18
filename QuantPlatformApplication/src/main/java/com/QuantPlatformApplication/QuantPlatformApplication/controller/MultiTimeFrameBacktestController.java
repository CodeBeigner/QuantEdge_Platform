package com.QuantPlatformApplication.QuantPlatformApplication.controller;

import com.QuantPlatformApplication.QuantPlatformApplication.engine.model.BacktestConfig;
import com.QuantPlatformApplication.QuantPlatformApplication.engine.model.MultiTimeFrameBacktestResult;
import com.QuantPlatformApplication.QuantPlatformApplication.service.MultiTimeFrameBacktestService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

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

        // TODO: Fetch 15m candles from Delta Exchange historical API or DB
        // For now, return method signature for Phase 4 wiring
        return ResponseEntity.ok(null);
    }
}
