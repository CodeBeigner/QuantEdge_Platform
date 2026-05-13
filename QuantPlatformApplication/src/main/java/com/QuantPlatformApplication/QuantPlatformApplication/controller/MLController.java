package com.QuantPlatformApplication.QuantPlatformApplication.controller;

import com.QuantPlatformApplication.QuantPlatformApplication.service.MLClientService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/ml")
@RequiredArgsConstructor
public class MLController {

    private final MLClientService mlClient;

    @PostMapping("/predict/{symbol}")
    public ResponseEntity<?> predict(@PathVariable String symbol) {
        return ResponseEntity.ok(mlClient.predict(symbol));
    }

    @PostMapping("/train/{symbol}")
    public ResponseEntity<?> train(@PathVariable String symbol) {
        return ResponseEntity.ok(mlClient.train(symbol));
    }

    @GetMapping("/features/{symbol}")
    public ResponseEntity<?> features(@PathVariable String symbol) {
        return ResponseEntity.ok(mlClient.getFeatures(symbol));
    }

    @PostMapping("/optimize")
    public ResponseEntity<?> optimize(@RequestBody Map<String, List<String>> body) {
        List<String> symbols = body.get("symbols");
        return ResponseEntity.ok(mlClient.optimize(symbols));
    }

    @GetMapping("/signals")
    public ResponseEntity<?> recentSignals() {
        return ResponseEntity.ok(mlClient.getRecentSignals());
    }

    @GetMapping("/health")
    public ResponseEntity<?> health() {
        return ResponseEntity.ok(mlClient.health());
    }

    public record PredictMetaRequest(
            String primarySignal,
            Double entryPrice,
            Double tpPct,
            Double slPct
    ) {}

    public record PredictFlowRequest(Integer lookbackBars) {}

    @PostMapping("/train-meta/{symbol}")
    public ResponseEntity<?> trainMeta(@PathVariable String symbol) {
        return ResponseEntity.ok(mlClient.trainMeta(symbol));
    }

    @PostMapping("/predict-meta/{symbol}")
    public ResponseEntity<?> predictMeta(
            @PathVariable String symbol,
            @RequestBody PredictMetaRequest req) {
        double entryPrice = req.entryPrice() != null ? req.entryPrice() : 0.0;
        double tpPct = req.tpPct() != null ? req.tpPct() : 0.02;
        double slPct = req.slPct() != null ? req.slPct() : 0.01;
        String primary = req.primarySignal() != null ? req.primarySignal() : "LONG";
        return ResponseEntity.ok(mlClient.predictMeta(symbol, primary, entryPrice, tpPct, slPct));
    }

    @PostMapping("/train-flow/{symbol}")
    public ResponseEntity<?> trainFlow(@PathVariable String symbol) {
        return ResponseEntity.ok(mlClient.trainFlow(symbol));
    }

    @PostMapping("/predict-flow/{symbol}")
    public ResponseEntity<?> predictFlow(
            @PathVariable String symbol,
            @RequestBody(required = false) PredictFlowRequest req) {
        int lookback = (req != null && req.lookbackBars() != null) ? req.lookbackBars() : 200;
        return ResponseEntity.ok(mlClient.predictFlow(symbol, lookback));
    }
}
