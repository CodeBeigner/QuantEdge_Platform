package com.QuantPlatformApplication.QuantPlatformApplication.service;

import com.QuantPlatformApplication.QuantPlatformApplication.model.entity.AgentRole;
import com.QuantPlatformApplication.QuantPlatformApplication.model.entity.TradingAgent;
import com.QuantPlatformApplication.QuantPlatformApplication.repository.TradingAgentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.Executors;

/**
 * Event-driven agent activation system.
 * Triggers agents based on market events rather than fixed cron schedules.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EventDrivenAgentService {

    private final TradingAgentRepository agentRepository;
    private final AgentPipelineService pipelineService;
    private final AgentConsensusService consensusService;
    private final ClaudeAgentService claudeAgent;
    private final RiskEngineService riskEngine;

    /**
     * Trigger agents on market regime change.
     */
    public void onRegimeChange(String newRegime, String symbol) {
        log.info("Regime change detected: {} for {}. Triggering relevant agents.", newRegime, symbol);
        
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<TradingAgent> agents = agentRepository.findAll().stream()
                    .filter(a -> Boolean.TRUE.equals(a.getActive()))
                    .toList();

            for (TradingAgent agent : agents) {
                executor.submit(() -> {
                    try {
                        log.info("Re-evaluating agent {} due to regime change to {}", agent.getId(), newRegime);
                        pipelineService.runResearchPipeline(agent.getStrategyId(), symbol);
                    } catch (Exception e) {
                        log.error("Failed to re-evaluate agent {} on regime change: {}", agent.getId(), e.getMessage());
                    }
                });
            }
        }
    }

    /**
     * Trigger risk agents on VaR breach.
     */
    public void onVaRBreach(String symbol, double varValue) {
        log.warn("VaR breach detected for {}: {}. Triggering risk agents.", symbol, varValue);

        String context = String.format(
                "{\"event\": \"VAR_BREACH\", \"symbol\": \"%s\", \"var_value\": %f, \"portfolio_risk\": %s}",
                symbol, varValue, riskEngine.getPortfolioRisk()
        );

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            executor.submit(() -> claudeAgent.runAgent(AgentRole.RISK_ANALYST, context));
            executor.submit(() -> claudeAgent.runAgent(AgentRole.EXECUTION_MONITOR, context));
        }
    }

    /**
     * Trigger performance analysis on signal drift.
     */
    public void onSignalDrift(Long strategyId, String symbol, double ic) {
        log.warn("Signal drift detected for strategy {}: IC={}", strategyId, ic);

        String context = String.format(
                "{\"event\": \"SIGNAL_DRIFT\", \"strategy_id\": %d, \"symbol\": \"%s\", \"ic\": %f}",
                strategyId, symbol, ic
        );

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            executor.submit(() -> claudeAgent.runAgent(AgentRole.PERFORMANCE_ATTRIBUTOR, context));
            executor.submit(() -> claudeAgent.runAgent(AgentRole.QUANT_RESEARCHER, context));
        }
    }

    /**
     * Post-trade analysis trigger.
     */
    public void onLargeOrderFill(String symbol, double quantity, double price) {
        log.info("Large order fill detected: {} {} @ {}", symbol, quantity, price);

        String context = String.format(
                "{\"event\": \"LARGE_FILL\", \"symbol\": \"%s\", \"quantity\": %f, \"price\": %f}",
                symbol, quantity, price
        );

        claudeAgent.runAgent(AgentRole.EXECUTION_OPTIMIZER, context);
    }
}
