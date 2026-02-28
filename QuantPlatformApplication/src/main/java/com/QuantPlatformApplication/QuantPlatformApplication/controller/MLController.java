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
}
