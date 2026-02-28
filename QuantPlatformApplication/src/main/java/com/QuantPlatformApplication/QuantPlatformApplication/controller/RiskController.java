package com.QuantPlatformApplication.QuantPlatformApplication.controller;

import com.QuantPlatformApplication.QuantPlatformApplication.service.RiskEngineService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/risk")
@RequiredArgsConstructor
public class RiskController {

    private final RiskEngineService riskEngine;

    @GetMapping("/var/{symbol}")
    public ResponseEntity<Map<String, Object>> getVaR(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "252") int days) {
        return ResponseEntity.ok(riskEngine.calculateVaR(symbol, days));
    }

    @GetMapping("/positions")
    public ResponseEntity<Map<String, Object>> checkPositions() {
        return ResponseEntity.ok(riskEngine.checkPositionLimits());
    }

    @GetMapping("/portfolio")
    public ResponseEntity<Map<String, Object>> portfolioRisk() {
        return ResponseEntity.ok(riskEngine.getPortfolioRisk());
    }
}
