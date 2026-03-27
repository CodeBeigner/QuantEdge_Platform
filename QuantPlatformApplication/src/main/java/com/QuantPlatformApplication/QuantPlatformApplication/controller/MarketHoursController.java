package com.QuantPlatformApplication.QuantPlatformApplication.controller;

import com.QuantPlatformApplication.QuantPlatformApplication.service.MarketHoursService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/market-hours")
@RequiredArgsConstructor
public class MarketHoursController {

    private final MarketHoursService marketHoursService;

    @GetMapping
    public ResponseEntity<Map<String, Object>> getMarketStatus() {
        return ResponseEntity.ok(marketHoursService.getMarketStatus());
    }

    @GetMapping("/is-open")
    public ResponseEntity<Map<String, Boolean>> isMarketOpen() {
        return ResponseEntity.ok(Map.of("isOpen", marketHoursService.isMarketOpen()));
    }
}
