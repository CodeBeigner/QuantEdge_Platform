// controller/SystemHealthController.java
package com.QuantPlatformApplication.QuantPlatformApplication.controller;

import com.QuantPlatformApplication.QuantPlatformApplication.service.pipeline.AccountStateTracker;
import com.QuantPlatformApplication.QuantPlatformApplication.service.pipeline.FundingRateTracker;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/system")
@RequiredArgsConstructor
public class SystemHealthController {

    private final AccountStateTracker accountState;
    private final FundingRateTracker fundingRateTracker;

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> systemHealth() {
        Map<String, Object> health = new LinkedHashMap<>();
        health.put("status", "UP");
        health.put("timestamp", Instant.now().toString());
        health.put("version", "1.0.0");

        Map<String, Object> components = new LinkedHashMap<>();
        components.put("strategies", Map.of("status", "UP", "count", 3));
        components.put("riskEngine", Map.of("status", "UP"));
        components.put("telegram", Map.of("status", "CONFIGURED"));
        components.put("account", Map.of(
            "balance", accountState.getCurrentBalance(),
            "peakEquity", accountState.getPeakEquity(),
            "openPositions", accountState.getOpenPositionSymbols().size()
        ));
        components.put("fundingRate", Map.of(
            "current", fundingRateTracker.getCurrentRate(),
            "historySize", fundingRateTracker.getHistory().size()
        ));

        health.put("components", components);
        return ResponseEntity.ok(health);
    }

    @GetMapping("/version")
    public ResponseEntity<Map<String, String>> version() {
        return ResponseEntity.ok(Map.of(
            "name", "QuantEdge Platform",
            "version", "1.0.0",
            "phase", "Approach A — Rule-based multi-timeframe"
        ));
    }
}
