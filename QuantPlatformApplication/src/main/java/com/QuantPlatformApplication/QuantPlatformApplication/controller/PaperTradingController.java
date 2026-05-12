package com.QuantPlatformApplication.QuantPlatformApplication.controller;

import com.QuantPlatformApplication.QuantPlatformApplication.model.entity.TradeLog;
import com.QuantPlatformApplication.QuantPlatformApplication.repository.TradeLogRepository;
import com.QuantPlatformApplication.QuantPlatformApplication.service.paper.PaperMetricsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Read-only paper-trading metrics and trade history for the dashboard.
 */
@RestController
@RequestMapping("/api/v1/paper")
@RequiredArgsConstructor
public class PaperTradingController {

    private static final long SYSTEM_PAPER_USER_ID = 0L;

    private final PaperMetricsService metrics;
    private final TradeLogRepository tradeLogRepo;

    @GetMapping("/metrics")
    public ResponseEntity<Map<String, Object>> getMetrics(
            @RequestParam(defaultValue = "28") int windowDays) {
        PaperMetricsService.Metrics m = metrics.computeRolling(windowDays);
        PaperMetricsService.Gate g = metrics.gateStatus(m);
        return ResponseEntity.ok(Map.of(
            "metrics", m,
            "gate", g,
            "criteria", Map.of(
                "sharpe",   "> 1.5",
                "maxDD",    "< 15%",
                "winRate",  "55% - 65%",
                "trades",   "> 50",
                "window",   ">= 4 weeks"
            )
        ));
    }

    @GetMapping("/trades")
    public ResponseEntity<List<TradeLog>> getTrades(
            @RequestParam(required = false) String status) {
        if (status != null) {
            return ResponseEntity.ok(
                tradeLogRepo.findByUserIdAndStatusOrderByCreatedAtDesc(SYSTEM_PAPER_USER_ID, status));
        }
        return ResponseEntity.ok(tradeLogRepo.findByUserIdOrderByCreatedAtDesc(SYSTEM_PAPER_USER_ID));
    }
}
