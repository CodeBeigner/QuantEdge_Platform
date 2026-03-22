package com.QuantPlatformApplication.QuantPlatformApplication.controller;

import com.QuantPlatformApplication.QuantPlatformApplication.service.SignalTrackingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST controller for signal IC tracking and drift detection.
 *
 * <ul>
 *   <li>GET /api/v1/signals/ic/{strategyId}     — Rolling IC for a strategy</li>
 *   <li>GET /api/v1/signals/drift/{strategyId}  — Drift detection result</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/signals")
@RequiredArgsConstructor
public class SignalTrackingController {

    private final SignalTrackingService signalTrackingService;

    /**
     * Get the rolling Information Coefficient for a strategy.
     *
     * @param strategyId the strategy to analyze
     * @param window     rolling window in days (default 20)
     * @return IC value
     */
    @GetMapping("/ic/{strategyId}")
    public ResponseEntity<Map<String, Object>> getSignalIC(
            @PathVariable Long strategyId,
            @RequestParam(defaultValue = "20") int window) {
        double ic = signalTrackingService.computeRollingIC(strategyId, window);
        return ResponseEntity.ok(Map.of(
                "strategyId", strategyId,
                "window", window,
                "ic", Math.round(ic * 10000.0) / 10000.0
        ));
    }

    /**
     * Detect signal drift for a strategy.
     *
     * @param strategyId the strategy to analyze
     * @return drift detection result
     */
    @GetMapping("/drift/{strategyId}")
    public ResponseEntity<Map<String, Object>> detectDrift(
            @PathVariable Long strategyId) {
        return ResponseEntity.ok(signalTrackingService.detectDrift(strategyId));
    }
}
