package com.QuantPlatformApplication.QuantPlatformApplication.controller;

import com.QuantPlatformApplication.QuantPlatformApplication.service.pipeline.MarketDataSyncScheduler;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Admin-only triggers for data pipeline operations.
 * Authentication is enforced upstream by Spring Security (see SecurityConfig).
 */
@RestController
@RequestMapping("/api/v1/admin/market-data")
@RequiredArgsConstructor
public class AdminMarketDataController {

    private final MarketDataSyncScheduler scheduler;

    @PostMapping("/resync")
    public ResponseEntity<Map<String, Object>> resync() {
        scheduler.runOnce();
        return ResponseEntity.ok(Map.of(
            "status", "ok",
            "message", "Market data sync triggered; see logs for per-pair row counts"
        ));
    }
}
