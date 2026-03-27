package com.QuantPlatformApplication.QuantPlatformApplication.controller;

import com.QuantPlatformApplication.QuantPlatformApplication.service.AgentPerformanceTrackingService;
import com.QuantPlatformApplication.QuantPlatformApplication.service.AgentConsensusService;
import com.QuantPlatformApplication.QuantPlatformApplication.model.entity.AgentRole;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/agent-performance")
@RequiredArgsConstructor
public class AgentPerformanceController {

    private final AgentPerformanceTrackingService performanceService;
    private final AgentConsensusService consensusService;

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> getAllPerformance() {
        return ResponseEntity.ok(performanceService.getAllAgentPerformance());
    }

    @GetMapping("/{agentId}")
    public ResponseEntity<Map<String, Object>> getAgentPerformance(@PathVariable Long agentId) {
        return ResponseEntity.ok(performanceService.getAgentPerformanceMetrics(agentId));
    }

    @GetMapping("/aggregate")
    public ResponseEntity<Map<String, Object>> getAggregate() {
        return ResponseEntity.ok(performanceService.getAggregatePerformance());
    }

    @PostMapping("/consensus")
    public ResponseEntity<Map<String, Object>> seekConsensus(@RequestBody Map<String, String> request) {
        String context = request.getOrDefault("context", "{}");
        return ResponseEntity.ok(consensusService.preTradeConsensus(context));
    }
}
