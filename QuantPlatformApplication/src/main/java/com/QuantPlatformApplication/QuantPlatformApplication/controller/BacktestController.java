package com.QuantPlatformApplication.QuantPlatformApplication.controller;

import com.QuantPlatformApplication.QuantPlatformApplication.model.dto.BacktestRequest;
import com.QuantPlatformApplication.QuantPlatformApplication.model.dto.BacktestResponse;
import com.QuantPlatformApplication.QuantPlatformApplication.service.BacktestService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST controller for backtesting strategies against historical data.
 *
 * <ul>
 *   <li>POST /api/v1/backtests                          — Run a new backtest</li>
 *   <li>GET  /api/v1/backtests/strategy/{strategyId}    — Get all backtests for a strategy</li>
 *   <li>POST /api/v1/backtests/{strategyId}/walk-forward — Run walk-forward validation</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/backtests")
public class BacktestController {

    private final BacktestService backtestService;

    public BacktestController(BacktestService backtestService) {
        this.backtestService = backtestService;
    }

    @PostMapping
    public ResponseEntity<BacktestResponse> runBacktest(@Valid @RequestBody BacktestRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(backtestService.runBacktest(request));
    }

    @GetMapping("/strategy/{strategyId}")
    public ResponseEntity<List<BacktestResponse>> getBacktests(@PathVariable Long strategyId) {
        return ResponseEntity.ok(backtestService.getBacktestsByStrategy(strategyId));
    }

    /**
     * Run walk-forward validation for a strategy.
     * Slides a train/test window across historical data to test robustness.
     *
     * @param strategyId the strategy to validate
     * @return walk-forward result with per-window metrics and aggregate stats
     */
    @PostMapping("/{strategyId}/walk-forward")
    public ResponseEntity<Map<String, Object>> runWalkForward(@PathVariable Long strategyId) {
        return ResponseEntity.ok(backtestService.runWalkForwardBacktest(strategyId));
    }
}
