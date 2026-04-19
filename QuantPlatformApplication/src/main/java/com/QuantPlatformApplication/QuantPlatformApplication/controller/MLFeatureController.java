package com.QuantPlatformApplication.QuantPlatformApplication.controller;

import com.QuantPlatformApplication.QuantPlatformApplication.model.entity.MLFeatureSnapshot;
import com.QuantPlatformApplication.QuantPlatformApplication.repository.MLFeatureSnapshotRepository;
import com.QuantPlatformApplication.QuantPlatformApplication.service.MLFeatureCollector;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/ml/features")
@RequiredArgsConstructor
public class MLFeatureController {

    private final MLFeatureCollector featureCollector;
    private final MLFeatureSnapshotRepository repository;

    /**
     * Trigger one-time ML feature collection for a symbol.
     * POST /api/v1/ml/features/collect?symbol=BTCUSDT
     */
    @PostMapping("/collect")
    public ResponseEntity<MLFeatureSnapshot> collect(
            @RequestParam(defaultValue = "BTCUSDT") String symbol) {
        MLFeatureSnapshot snapshot = featureCollector.collectAndStore(symbol);
        return ResponseEntity.ok(snapshot);
    }

    /**
     * Get last 100 ML feature snapshots for a symbol.
     * GET /api/v1/ml/features/recent?symbol=BTCUSDT
     */
    @GetMapping("/recent")
    public ResponseEntity<List<MLFeatureSnapshot>> recent(
            @RequestParam(defaultValue = "BTCUSDT") String symbol) {
        List<MLFeatureSnapshot> snapshots = repository.findTop100BySymbolOrderByTimestampDesc(symbol);
        return ResponseEntity.ok(snapshots);
    }
}
