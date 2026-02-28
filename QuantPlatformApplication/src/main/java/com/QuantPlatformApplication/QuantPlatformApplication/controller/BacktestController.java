package com.QuantPlatformApplication.QuantPlatformApplication.controller;

import com.QuantPlatformApplication.QuantPlatformApplication.model.dto.BacktestRequest;
import com.QuantPlatformApplication.QuantPlatformApplication.model.dto.BacktestResponse;
import com.QuantPlatformApplication.QuantPlatformApplication.service.BacktestService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for backtesting strategies against historical data.

 * POST /api/v1/backtests — Run a new backtest
 * GET /api/v1/backtests/{sid} — Get all backtests for a strategy
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
}
