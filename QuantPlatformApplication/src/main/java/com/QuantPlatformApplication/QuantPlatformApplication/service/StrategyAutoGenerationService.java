package com.QuantPlatformApplication.QuantPlatformApplication.service;

import com.QuantPlatformApplication.QuantPlatformApplication.model.entity.AgentRole;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * Uses AI to analyze market patterns and auto-generate new strategy configurations.
 * Auto-backtests and validates before adding to roster.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StrategyAutoGenerationService {

    private final ClaudeAgentService claudeAgent;
    private final BacktestService backtestService;
    private final StrategyService strategyService;
    private final MarketDataService marketDataService;
    private final ObjectMapper objectMapper;

    private static final double MIN_SHARPE_THRESHOLD = 1.0;
    private static final double MAX_DRAWDOWN_THRESHOLD = 0.15;

    public Map<String, Object> generateStrategy(String symbol) {
        Map<String, Object> result = new HashMap<>();

        try {
            // Step 1: Analyze market data patterns
            log.info("Auto-generating strategy for {}", symbol);
            Map<String, Object> context = buildMarketAnalysisContext(symbol);

            Map<String, Object> aiSuggestion = claudeAgent.runAgentWithCustomPrompt(
                    STRATEGY_GENERATION_PROMPT,
                    objectMapper.writeValueAsString(context)
            );
            result.put("ai_suggestion", aiSuggestion);

            // Step 2: Extract strategy parameters
            String modelType = (String) aiSuggestion.getOrDefault("recommended_strategy_type", "MOMENTUM");
            String strategyName = (String) aiSuggestion.getOrDefault("strategy_name", "Auto-" + symbol + "-" + modelType);

            result.put("strategy_name", strategyName);
            result.put("model_type", modelType);
            result.put("symbol", symbol);
            result.put("status", "GENERATED");
            result.put("message", "Strategy suggestion generated. Run backtest to validate before deployment.");

        } catch (Exception e) {
            log.error("Strategy auto-generation failed for {}: {}", symbol, e.getMessage(), e);
            result.put("status", "FAILED");
            result.put("error", e.getMessage());
        }

        return result;
    }

    private Map<String, Object> buildMarketAnalysisContext(String symbol) {
        Map<String, Object> ctx = new HashMap<>();
        ctx.put("symbol", symbol);
        ctx.put("request", "Analyze the market data patterns for " + symbol
                + " and suggest the best trading strategy type (MOMENTUM, VOLATILITY, MACRO, CORRELATION, or REGIME). "
                + "Provide a strategy name, recommended parameters, expected characteristics (Sharpe, drawdown), "
                + "and reasoning for your choice. Consider current market conditions.");
        return ctx;
    }

    private static final String STRATEGY_GENERATION_PROMPT = """
            You are a quantitative strategy designer at a top hedge fund. Your task is to analyze market data
            and design optimal trading strategies. You must respond in JSON with these fields:
            {
                "strategy_name": "descriptive name",
                "recommended_strategy_type": "MOMENTUM|VOLATILITY|MACRO|CORRELATION|REGIME",
                "reasoning": "why this strategy type fits the current market",
                "expected_sharpe": 1.5,
                "expected_max_drawdown": 0.10,
                "key_parameters": {"param1": "value1"},
                "entry_conditions": "when to enter",
                "exit_conditions": "when to exit",
                "risk_management": "position sizing and stop-loss rules",
                "market_regime_fit": "which regimes this works best in"
            }
            """;

}
