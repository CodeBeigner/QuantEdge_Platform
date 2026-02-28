package com.QuantPlatformApplication.QuantPlatformApplication.controller;

import com.QuantPlatformApplication.QuantPlatformApplication.model.dto.ExecutionResponse;
import com.QuantPlatformApplication.QuantPlatformApplication.model.dto.StrategyRequest;
import com.QuantPlatformApplication.QuantPlatformApplication.model.dto.StrategyResponse;
import com.QuantPlatformApplication.QuantPlatformApplication.service.StrategyService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST controller for strategy CRUD and execution.

 * Endpoints:
 * POST /api/v1/strategies — Create a strategy
 * GET /api/v1/strategies — List all strategies
 * GET /api/v1/strategies/{id} — Get strategy by ID
 * PUT /api/v1/strategies/{id} — Update strategy
 * DELETE /api/v1/strategies/{id} — Delete strategy
 * POST /api/v1/strategies/{id}/execute — Execute strategy → get signal
 */
@RestController
@RequestMapping("/api/v1/strategies")
public class StrategyController {

    private final StrategyService strategyService;

    public StrategyController(StrategyService strategyService) {
        this.strategyService = strategyService;
    }

    // ─── CRUD ────────────────────────────────────────────────

    @PostMapping
    public ResponseEntity<?> createStrategy(@Valid @RequestBody StrategyRequest request) {
        try {
            StrategyResponse response = strategyService.createStrategy(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping
    public ResponseEntity<List<StrategyResponse>> getAllStrategies() {
        return ResponseEntity.ok(strategyService.getAllStrategies());
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getStrategy(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(strategyService.getStrategy(id));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateStrategy(@PathVariable Long id,
            @Valid @RequestBody StrategyRequest request) {
        try {
            return ResponseEntity.ok(strategyService.updateStrategy(id, request));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteStrategy(@PathVariable Long id) {
        try {
            strategyService.deleteStrategy(id);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }

    // ─── EXECUTION ───────────────────────────────────────────

    /**
     * Execute a strategy against real market data.
     * Returns the trading signal: { "action": "BUY", "reasoning": "...", ... }
     */
    @PostMapping("/{id}/execute")
    public ResponseEntity<?> executeStrategy(@PathVariable Long id) {
        try {
            ExecutionResponse response = strategyService.executeStrategy(id);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }
}
