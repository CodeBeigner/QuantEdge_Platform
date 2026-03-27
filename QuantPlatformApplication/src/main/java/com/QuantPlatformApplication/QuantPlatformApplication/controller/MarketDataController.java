package com.QuantPlatformApplication.QuantPlatformApplication.controller;

import com.QuantPlatformApplication.QuantPlatformApplication.model.dto.MarketDataResponse;
import com.QuantPlatformApplication.QuantPlatformApplication.model.entity.MarketDataEntity;
import com.QuantPlatformApplication.QuantPlatformApplication.service.MarketDataService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

/**
 * REST controller for market data endpoints.

 * Endpoints:
 * GET /api/v1/market-data/prices/{symbol}?days=252 — Last N trading days
 * GET /api/v1/market-data/prices/{symbol}?start=...&end=... — Date range query
 * GET /api/v1/market-data/symbols — List available symbols

 * Design decisions:
 * - Returns DTOs, never raw entities (see MarketDataResponse)
 * - Uses query parameters for flexible filtering
 * - days parameter takes priority over start/end when both provided
 */
@RestController
@RequestMapping("/api/v1/market-data")
public class MarketDataController {

    private final MarketDataService marketDataService;

    public MarketDataController(MarketDataService marketDataService) {
        this.marketDataService = marketDataService;
    }

    /**
     * GET /api/v1/market-data/prices/{symbol}

     * Fetch historical OHLCV data for a symbol.

     * Query params (choose one mode):
     * ?days=252 → most recent 252 trading days
     * ?start=2025-01-01&end=2025-12-31 → specific date range

     * If no params provided, defaults to last 30 days.
     */
    @GetMapping("/prices/{symbol}")
    public ResponseEntity<?> getPrices(
            @PathVariable String symbol,
            @RequestParam(required = false) Integer days,
            @RequestParam(required = false) String start,
            @RequestParam(required = false) String end) {

        try {
            List<MarketDataEntity> data;

            if (days != null) {
                // Mode 1: Last N days
                data = marketDataService.fetchRecentData(symbol, days);
            } else if (start != null && end != null) {
                // Mode 2: Date range
                Instant startInstant = LocalDate.parse(start).atStartOfDay(ZoneOffset.UTC).toInstant();
                Instant endInstant = LocalDate.parse(end).atTime(23, 59, 59).toInstant(ZoneOffset.UTC);
                data = marketDataService.fetchDailyData(symbol, startInstant, endInstant);
            } else {
                // Default: last 30 days
                data = marketDataService.fetchRecentData(symbol, 30);
            }

            // Convert entities to DTOs
            List<MarketDataResponse> response = data.stream()
                    .map(this::toResponse)
                    .toList();

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getClass().getSimpleName(), "message", e.getMessage() != null ? e.getMessage() : "unknown"));
        }
    }

    /**
     * GET /api/v1/market-data/symbols

     * List all ticker symbols available in the database.
     */
    @GetMapping("/symbols")
    public ResponseEntity<List<String>> getSymbols() {
        return ResponseEntity.ok(marketDataService.getAvailableSymbols());
    }

    /**
     * GET /api/v1/market-data/summary/{symbol}

     * Quick summary for a symbol: total record count.
     */
    @GetMapping("/summary/{symbol}")
    public ResponseEntity<Map<String, Object>> getSummary(@PathVariable String symbol) {
        long count = marketDataService.getRecordCount(symbol);
        return ResponseEntity.ok(Map.of(
                "symbol", symbol.toUpperCase(),
                "totalRecords", count));
    }

    // ─── Private helpers ─────────────────────────────────

    /**
     * Convert entity → DTO.
     * This keeps JPA internals out of the API response.
     */
    private MarketDataResponse toResponse(MarketDataEntity entity) {
        return MarketDataResponse.builder()
                .time(entity.getTime())
                .symbol(entity.getSymbol())
                .open(entity.getOpen())
                .high(entity.getHigh())
                .low(entity.getLow())
                .close(entity.getClose())
                .volume(entity.getVolume())
                .build();
    }
}
