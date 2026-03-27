package com.QuantPlatformApplication.QuantPlatformApplication.controller;

import com.QuantPlatformApplication.QuantPlatformApplication.service.broker.BrokerManager;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/broker")
@RequiredArgsConstructor
public class BrokerController {

    private final BrokerManager brokerManager;

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> listBrokers() {
        return ResponseEntity.ok(brokerManager.listBrokers());
    }

    @PostMapping("/switch")
    public ResponseEntity<Map<String, String>> switchBroker(@RequestBody Map<String, String> request) {
        String name = request.get("broker");
        brokerManager.setActiveBroker(name);
        return ResponseEntity.ok(Map.of("status", "switched", "activeBroker", name));
    }

    @GetMapping("/account")
    public ResponseEntity<Map<String, Object>> getAccount() {
        return ResponseEntity.ok(brokerManager.getActiveBroker().getAccount());
    }

    @PostMapping("/reconcile")
    public ResponseEntity<Map<String, Object>> reconcile() {
        return ResponseEntity.ok(brokerManager.getActiveBroker().reconcilePositions());
    }
}
