package com.QuantPlatformApplication.QuantPlatformApplication.service;

import com.QuantPlatformApplication.QuantPlatformApplication.model.entity.TradingAgent;
import com.QuantPlatformApplication.QuantPlatformApplication.repository.TradingAgentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Tracks per-agent performance metrics for the performance dashboard.
 * Provides P&L attribution, accuracy tracking, and cost analysis.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentPerformanceTrackingService {

    private final TradingAgentRepository agentRepository;

    public Map<String, Object> getAgentPerformanceMetrics(Long agentId) {
        TradingAgent agent = agentRepository.findById(agentId)
                .orElseThrow(() -> new IllegalArgumentException("Agent not found: " + agentId));

        int total = agent.getTotalExecutions() != null ? agent.getTotalExecutions() : 0;
        int success = agent.getSuccessfulExecutions() != null ? agent.getSuccessfulExecutions() : 0;
        double successRate = total > 0 ? (double) success / total * 100 : 0;

        return Map.of(
                "agentId", agent.getId(),
                "agentName", agent.getName(),
                "role", agent.getAgentRole() != null ? agent.getAgentRole().name() : "UNKNOWN",
                "totalExecutions", total,
                "successfulExecutions", success,
                "successRate", Math.round(successRate * 100) / 100.0,
                "lastConfidence", agent.getLastConfidence() != null ? agent.getLastConfidence() : 0,
                "isActive", Boolean.TRUE.equals(agent.getActive()),
                "lifecycleState", agent.getLifecycleState() != null ? agent.getLifecycleState() : "CREATED",
                "lastRunAt", agent.getLastRunAt() != null ? agent.getLastRunAt().toString() : "Never"
        );
    }

    public List<Map<String, Object>> getAllAgentPerformance() {
        return agentRepository.findAll().stream()
                .map(agent -> getAgentPerformanceMetrics(agent.getId()))
                .collect(Collectors.toList());
    }

    public Map<String, Object> getAggregatePerformance() {
        List<TradingAgent> agents = agentRepository.findAll();
        
        int totalExecutions = agents.stream()
                .mapToInt(a -> a.getTotalExecutions() != null ? a.getTotalExecutions() : 0)
                .sum();
        int totalSuccess = agents.stream()
                .mapToInt(a -> a.getSuccessfulExecutions() != null ? a.getSuccessfulExecutions() : 0)
                .sum();
        long activeCount = agents.stream().filter(a -> Boolean.TRUE.equals(a.getActive())).count();
        double avgConfidence = agents.stream()
                .filter(a -> a.getLastConfidence() != null)
                .mapToDouble(TradingAgent::getLastConfidence)
                .average()
                .orElse(0);

        return Map.of(
                "totalAgents", agents.size(),
                "activeAgents", activeCount,
                "totalExecutions", totalExecutions,
                "totalSuccessful", totalSuccess,
                "overallSuccessRate", totalExecutions > 0 ? Math.round((double) totalSuccess / totalExecutions * 10000) / 100.0 : 0,
                "averageConfidence", Math.round(avgConfidence * 10000) / 10000.0
        );
    }
}
