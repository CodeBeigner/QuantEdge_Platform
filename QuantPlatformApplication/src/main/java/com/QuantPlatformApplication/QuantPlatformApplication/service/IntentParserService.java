package com.QuantPlatformApplication.QuantPlatformApplication.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.*;

/**
 * Parses CEO natural language commands into executable intents.
 * Maps phrases to concrete service calls.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IntentParserService {

    private final OrderManagementService orderService;
    private final RiskEngineService riskEngine;
    private final AgentSchedulerService agentScheduler;
    private final StrategyService strategyService;
    private final AgentPipelineService pipelineService;
    private final MLClientService mlClient;

    public record Intent(String action, Map<String, Object> params, String description) {}

    /**
     * Parse a natural language command and execute it if a known intent is matched.
     */
    public Map<String, Object> parseAndExecute(String command) {
        String lower = command.toLowerCase().trim();

        // Risk queries
        if (lower.matches(".*(risk|exposure|var|drawdown).*")) {
            return executeRiskQuery(lower);
        }

        // Agent control
        if (lower.matches(".*(pause|stop|halt).*agent.*") || lower.matches(".*agent.*(pause|stop|halt).*")) {
            return executeAgentControl(lower, false);
        }
        if (lower.matches(".*(start|resume|activate).*agent.*") || lower.matches(".*agent.*(start|resume|activate).*")) {
            return executeAgentControl(lower, true);
        }

        // Pipeline
        if (lower.matches(".*(run|execute|trigger).*pipeline.*")) {
            return executePipelineTrigger(lower);
        }

        // ML predictions
        if (lower.matches(".*(predict|signal|forecast).*")) {
            return executeMLPrediction(lower);
        }

        // Portfolio query
        if (lower.matches(".*(portfolio|position|holding).*")) {
            return executePortfolioQuery();
        }

        // Market status
        if (lower.matches(".*(market|status|health).*")) {
            return executeMarketStatus();
        }

        // No intent matched — fall through to Claude
        return Map.of(
                "intent", "UNRECOGNIZED",
                "message", "Command not matched to a known action. Forwarding to AI agent.",
                "original_command", command
        );
    }

    private Map<String, Object> executeRiskQuery(String command) {
        try {
            Map<String, Object> risk = riskEngine.getPortfolioRisk();
            return Map.of(
                    "intent", "RISK_QUERY",
                    "result", risk,
                    "message", "Here's the current portfolio risk assessment."
            );
        } catch (Exception e) {
            return Map.of("intent", "RISK_QUERY", "error", e.getMessage());
        }
    }

    private Map<String, Object> executeAgentControl(String command, boolean start) {
        // Extract agent ID from command
        Matcher m = Pattern.compile("agent\\s*(\\d+)").matcher(command);
        if (!m.find()) {
            // Try to pause/start all agents
            try {
                var agents = agentScheduler.getAllAgents();
                for (var agent : agents) {
                    if (start) agentScheduler.startAgent(agent.getId());
                    else agentScheduler.stopAgent(agent.getId());
                }
                return Map.of(
                        "intent", start ? "START_ALL_AGENTS" : "STOP_ALL_AGENTS",
                        "message", (start ? "Started " : "Stopped ") + agents.size() + " agents"
                );
            } catch (Exception e) {
                return Map.of("intent", "AGENT_CONTROL", "error", e.getMessage());
            }
        }

        Long agentId = Long.parseLong(m.group(1));
        try {
            if (start) agentScheduler.startAgent(agentId);
            else agentScheduler.stopAgent(agentId);
            return Map.of(
                    "intent", start ? "START_AGENT" : "STOP_AGENT",
                    "agentId", agentId,
                    "message", (start ? "Started" : "Stopped") + " agent " + agentId
            );
        } catch (Exception e) {
            return Map.of("intent", "AGENT_CONTROL", "error", e.getMessage());
        }
    }

    private Map<String, Object> executePipelineTrigger(String command) {
        // Extract symbol
        Matcher m = Pattern.compile("(?:for|on)\\s+(\\w+)").matcher(command);
        String symbol = m.find() ? m.group(1).toUpperCase() : "SPY";
        
        return Map.of(
                "intent", "PIPELINE_TRIGGER",
                "symbol", symbol,
                "message", "Pipeline trigger prepared for " + symbol + ". Select a strategy to run the full research pipeline."
        );
    }

    private Map<String, Object> executeMLPrediction(String command) {
        Matcher m = Pattern.compile("(?:for|on)\\s+(\\w+)").matcher(command);
        String symbol = m.find() ? m.group(1).toUpperCase() : "SPY";
        
        try {
            Map<String, Object> prediction = mlClient.predict(symbol);
            return Map.of(
                    "intent", "ML_PREDICTION",
                    "symbol", symbol,
                    "result", prediction,
                    "message", "ML prediction for " + symbol
            );
        } catch (Exception e) {
            return Map.of("intent", "ML_PREDICTION", "error", e.getMessage());
        }
    }

    private Map<String, Object> executePortfolioQuery() {
        try {
            Map<String, Object> portfolio = orderService.getPortfolioSummary();
            return Map.of(
                    "intent", "PORTFOLIO_QUERY",
                    "result", portfolio,
                    "message", "Current portfolio status"
            );
        } catch (Exception e) {
            return Map.of("intent", "PORTFOLIO_QUERY", "error", e.getMessage());
        }
    }

    private Map<String, Object> executeMarketStatus() {
        return Map.of(
                "intent", "MARKET_STATUS",
                "message", "Market status query processed"
        );
    }
}
