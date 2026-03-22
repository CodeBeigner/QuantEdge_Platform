package com.QuantPlatformApplication.QuantPlatformApplication.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.QuantPlatformApplication.QuantPlatformApplication.model.entity.AgentRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * Orchestrates multi-stage AI agent pipelines for strategy research and attribution.
 *
 * <p><b>Research Pipeline</b> (5 stages):
 * <ol>
 *   <li>Market Regime Analysis — classifies current regime and sets strategy weights</li>
 *   <li>Quant Research — evaluates strategy for deployment readiness</li>
 *   <li>Bias Audit — checks for lookahead, survivorship, overfitting biases</li>
 *   <li>Risk Analysis — pre-trade risk approval with VaR/position limits</li>
 *   <li>Psychology Check — enforces trading discipline rules</li>
 * </ol>
 *
 * <p>Each stage passes its output as context to the next agent.
 * The pipeline aborts early if the bias audit fails (can_proceed=false).
 *
 * <p><b>Attribution Pipeline</b>: Post-trade P&amp;L decomposition and
 * strategy health assessment.
 *
 * @see ClaudeAgentService
 * @see AgentRole
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentPipelineService {

    private final ClaudeAgentService claudeAgent;
    private final RiskEngineService riskEngine;
    private final MarketDataService marketDataService;
    private final RegimeDetectionService regimeService;
    private final BacktestService backtestService;
    private final ObjectMapper objectMapper;

    /**
     * Full research pipeline for a strategy before deployment decision.
     * Runs: RegimeAnalyst → QuantResearcher → BiasAuditor → RiskAnalyst → PsychologyEnforcer.
     *
     * @param strategyId the strategy to evaluate
     * @param symbol     the trading symbol (e.g. "SPY")
     * @return pipeline result map with all agent outputs and final decision
     */
    public Map<String, Object> runResearchPipeline(Long strategyId, String symbol) {
        Map<String, Object> pipelineResult = new HashMap<>();

        try {
            // Stage 1: Market Regime Analysis
            log.info("Pipeline Stage 1: Regime Analysis for symbol={}", symbol);
            Map<String, Object> regimeContext = buildRegimeContext(symbol);
            Map<String, Object> regimeResult = claudeAgent.runAgent(
                AgentRole.MARKET_REGIME_ANALYST,
                objectMapper.writeValueAsString(regimeContext)
            );
            pipelineResult.put("regime_analysis", regimeResult);

            // Stage 2: Quant Research (uses regime as context)
            log.info("Pipeline Stage 2: Quant Research for strategyId={}", strategyId);
            Map<String, Object> researchContext = buildResearchContext(strategyId, symbol, regimeResult);
            Map<String, Object> researchResult = claudeAgent.runAgent(
                AgentRole.QUANT_RESEARCHER,
                objectMapper.writeValueAsString(researchContext)
            );
            pipelineResult.put("research_decision", researchResult);

            // Stage 3: Bias Audit (uses research as context)
            log.info("Pipeline Stage 3: Bias Audit for strategyId={}", strategyId);
            Map<String, Object> auditContext = buildAuditContext(strategyId, researchResult);
            Map<String, Object> auditResult = claudeAgent.runAgent(
                AgentRole.BIAS_AUDITOR,
                objectMapper.writeValueAsString(auditContext)
            );
            pipelineResult.put("bias_audit", auditResult);

            // Stage 4: Risk Check (abort pipeline if bias audit fails)
            boolean auditPassed = Boolean.TRUE.equals(auditResult.get("can_proceed"));
            if (!auditPassed) {
                pipelineResult.put("pipeline_status", "REJECTED_BY_BIAS_AUDIT");
                pipelineResult.put("pipeline_complete", false);
                log.warn("Pipeline REJECTED at bias audit for strategyId={}", strategyId);
                return pipelineResult;
            }

            log.info("Pipeline Stage 4: Risk Analysis for strategyId={}", strategyId);
            Map<String, Object> riskContext = buildRiskContext(symbol, researchResult, auditResult);
            Map<String, Object> riskResult = claudeAgent.runAgent(
                AgentRole.RISK_ANALYST,
                objectMapper.writeValueAsString(riskContext)
            );
            pipelineResult.put("risk_assessment", riskResult);

            // Stage 5: Psychology Check
            log.info("Pipeline Stage 5: Psychology Check for strategyId={}", strategyId);
            Map<String, Object> psychContext = buildPsychContext(strategyId, riskResult);
            Map<String, Object> psychResult = claudeAgent.runAgent(
                AgentRole.PSYCHOLOGY_ENFORCER,
                objectMapper.writeValueAsString(psychContext)
            );
            pipelineResult.put("psychology_check", psychResult);

            // Final pipeline status
            boolean psychPass = "PASS".equals(psychResult.get("psychology_check"));
            String riskDecision = (String) riskResult.getOrDefault("risk_decision", "REJECT");
            String researchDecision = (String) researchResult.getOrDefault("decision", "REJECT");

            boolean canDeploy = psychPass
                && !"REJECT".equals(riskDecision)
                && ("DEPLOY".equals(researchDecision) || "PAPER_TEST".equals(researchDecision));

            pipelineResult.put("pipeline_status", canDeploy ? "APPROVED" : "REJECTED");
            pipelineResult.put("pipeline_complete", true);
            pipelineResult.put("final_decision", canDeploy ? researchDecision : "REJECT");

            log.info("Pipeline complete for strategyId={}. Status: {}",
                strategyId, pipelineResult.get("pipeline_status"));

        } catch (Exception e) {
            log.error("Pipeline failed for strategyId={}: {}", strategyId, e.getMessage(), e);
            pipelineResult.put("pipeline_status", "ERROR");
            pipelineResult.put("error", e.getMessage());
        }

        return pipelineResult;
    }

    /**
     * Performance attribution pipeline — runs after trading.
     * Analyzes P&amp;L and triggers re-research if strategy is degrading.
     *
     * @param strategyId the strategy to analyze
     * @param symbol     the trading symbol
     * @return attribution result map
     */
    public Map<String, Object> runAttributionPipeline(Long strategyId, String symbol) {
        try {
            Map<String, Object> attributionContext = buildAttributionContext(strategyId, symbol);
            Map<String, Object> result = claudeAgent.runAgent(
                AgentRole.PERFORMANCE_ATTRIBUTOR,
                objectMapper.writeValueAsString(attributionContext)
            );

            // If strategy is degrading, trigger re-research
            if (Boolean.TRUE.equals(result.get("trigger_re_research"))) {
                log.warn("Strategy {} flagged for re-research: {}",
                        strategyId, result.get("trigger_reason"));
            }

            return result;
        } catch (Exception e) {
            log.error("Attribution pipeline failed for strategyId={}: {}", strategyId, e.getMessage(), e);
            return Map.of("error", e.getMessage());
        }
    }

    /**
     * HFT Systems Engineering audit pipeline.
     *
     * <p>Invokes the HFT_SYSTEMS_ENGINEER agent to analyze the platform's
     * architecture, identify latency bottlenecks, and provide code-level
     * optimization suggestions — thinking like a principal engineer at a
     * top-tier HFT firm.
     *
     * @param symbol trading symbol for market data context
     * @return system audit result with optimizations, critical issues, and production readiness
     */
    public Map<String, Object> runSystemAuditPipeline(String symbol) {
        try {
            log.info("Running HFT Systems Audit for symbol={}", symbol);
            Map<String, Object> systemContext = buildSystemAuditContext(symbol);
            Map<String, Object> result = claudeAgent.runAgent(
                AgentRole.HFT_SYSTEMS_ENGINEER,
                objectMapper.writeValueAsString(systemContext)
            );
            log.info("Systems audit complete. Health score: {}, Assessment: {}",
                    result.get("system_health_score"), result.get("architecture_assessment"));
            return result;
        } catch (Exception e) {
            log.error("System audit pipeline failed: {}", e.getMessage(), e);
            return Map.of("error", e.getMessage());
        }
    }

    /**
     * Execution monitoring pipeline.
     *
     * <p>Invokes the EXECUTION_MONITOR agent to perform real-time surveillance
     * of all active algorithms. Detects anomalies (volume, price, frequency),
     * evaluates fill quality, and recommends circuit breaker actions.
     *
     * @param symbol trading symbol for monitoring context
     * @return monitoring result with anomalies, circuit breaker level, and exec quality
     */
    public Map<String, Object> runExecutionMonitorPipeline(String symbol) {
        try {
            log.info("Running Execution Monitor for symbol={}", symbol);
            Map<String, Object> monitorContext = buildExecutionMonitorContext(symbol);
            Map<String, Object> result = claudeAgent.runAgent(
                AgentRole.EXECUTION_MONITOR,
                objectMapper.writeValueAsString(monitorContext)
            );

            // If circuit breaker level >= 3, log critical alert
            Object cbLevel = result.get("circuit_breaker_level");
            if (cbLevel instanceof Number && ((Number) cbLevel).intValue() >= 3) {
                log.error("CRITICAL: Execution Monitor triggered circuit breaker level {} for symbol={}",
                        cbLevel, symbol);
            }

            log.info("Execution monitor complete. Status: {}, Circuit breaker: {}",
                    result.get("monitoring_status"), cbLevel);
            return result;
        } catch (Exception e) {
            log.error("Execution monitor pipeline failed: {}", e.getMessage(), e);
            return Map.of("error", e.getMessage());
        }
    }

    // ── Context builders ─────────────────────────────────────────────────

    private Map<String, Object> buildRegimeContext(String symbol) {
        Map<String, Object> ctx = new HashMap<>();
        ctx.put("symbol", symbol);
        ctx.put("vix", regimeService.getCurrentVix());
        ctx.put("yield_curve_spread", regimeService.getYieldCurveSpread());
        ctx.put("inflation_rate", regimeService.getInflationRate());
        ctx.put("request", "Classify the current market regime and provide strategy weights");
        return ctx;
    }

    private Map<String, Object> buildResearchContext(Long strategyId, String symbol,
            Map<String, Object> regimeResult) {
        Map<String, Object> ctx = new HashMap<>();
        ctx.put("strategy_id", strategyId);
        ctx.put("symbol", symbol);
        ctx.put("regime_context", regimeResult);
        ctx.put("request", "Evaluate this strategy for deployment readiness. " +
            "Include Sharpe ratio threshold check, drawdown analysis, and regime fit assessment.");
        // Attach last backtest result if available
        backtestService.getLastBacktestForStrategy(strategyId).ifPresent(bt -> {
            ctx.put("backtest_sharpe", bt.getSharpeRatio());
            ctx.put("backtest_max_drawdown", bt.getMaxDrawdown());
            ctx.put("backtest_win_rate", bt.getWinRate());
            ctx.put("backtest_total_trades", bt.getTotalTrades());
            ctx.put("backtest_total_return", bt.getTotalReturn());
        });
        return ctx;
    }

    private Map<String, Object> buildAuditContext(Long strategyId, Map<String, Object> researchResult) {
        Map<String, Object> ctx = new HashMap<>();
        ctx.put("strategy_id", strategyId);
        ctx.put("research_result", researchResult);
        ctx.put("request", "Perform a comprehensive bias audit on this strategy's research findings. " +
            "Check for lookahead bias, survivorship bias, and overfitting.");
        return ctx;
    }

    private Map<String, Object> buildRiskContext(String symbol,
            Map<String, Object> researchResult, Map<String, Object> auditResult) {
        Map<String, Object> ctx = new HashMap<>();
        ctx.put("symbol", symbol);
        ctx.put("research_result", researchResult);
        ctx.put("audit_result", auditResult);
        ctx.put("var_data", riskEngine.calculateVaR(symbol, 30));
        ctx.put("position_limits", riskEngine.checkPositionLimits());
        ctx.put("portfolio_risk", riskEngine.getPortfolioRisk());
        ctx.put("request", "Assess risk for deploying this strategy. " +
            "Provide APPROVE, REJECT, or APPROVE_REDUCED decision with stop-loss levels.");
        return ctx;
    }

    private Map<String, Object> buildPsychContext(Long strategyId, Map<String, Object> riskResult) {
        Map<String, Object> ctx = new HashMap<>();
        ctx.put("strategy_id", strategyId);
        ctx.put("risk_assessment", riskResult);
        ctx.put("request", "Check for psychological trading biases and enforce discipline rules. " +
            "Validate that this strategy follows the trading plan without emotional deviations.");
        return ctx;
    }

    private Map<String, Object> buildAttributionContext(Long strategyId, String symbol) {
        Map<String, Object> ctx = new HashMap<>();
        ctx.put("strategy_id", strategyId);
        ctx.put("symbol", symbol);
        ctx.put("request", "Perform P&L attribution and assess strategy health. " +
            "Determine if the strategy alpha is intact or decaying.");
        return ctx;
    }

    private Map<String, Object> buildSystemAuditContext(String symbol) {
        Map<String, Object> ctx = new HashMap<>();
        ctx.put("symbol", symbol);
        ctx.put("platform_stack", Map.of(
            "backend", "Java 21 + Spring Boot 3.5",
            "database", "TimescaleDB (PostgreSQL 15)",
            "cache", "Redis 7",
            "messaging", "Apache Kafka (Confluent 7.5)",
            "ml_service", "Python 3.12 + FastAPI + XGBoost + PyTorch LSTM",
            "frontend", "React 18 (CDN) + Recharts + WebSocket STOMP",
            "ai_agent", "Anthropic Claude Opus via Java SDK"
        ));
        ctx.put("architecture_components", Map.of(
            "order_management", "REST API → OrderManagementService → simulated fills",
            "risk_engine", "VaR (historical + parametric), position limits, portfolio risk",
            "strategy_execution", "StrategyService → BacktestEngine → TradingStrategy implementations",
            "agent_pipeline", "5-stage Claude AI pipeline (Regime→Research→Bias→Risk→Psychology)",
            "market_data", "MarketDataService → TimescaleDB hypertables",
            "event_system", "Kafka topics: market-data-events, agent-pipeline-events, risk-alerts, signal-predictions, order-fills"
        ));
        ctx.put("current_risk_state", riskEngine.getPortfolioRisk());
        ctx.put("request", "Perform a comprehensive systems engineering audit of this trading platform. " +
            "Analyze the architecture for latency bottlenecks, throughput limits, reliability gaps, " +
            "and code quality issues. Provide prioritized, actionable optimization suggestions " +
            "as if you were preparing this for live trading at a top HFT firm.");
        return ctx;
    }

    private Map<String, Object> buildExecutionMonitorContext(String symbol) {
        Map<String, Object> ctx = new HashMap<>();
        ctx.put("symbol", symbol);
        ctx.put("portfolio_risk", riskEngine.getPortfolioRisk());
        ctx.put("position_limits", riskEngine.checkPositionLimits());
        ctx.put("var_data", riskEngine.calculateVaR(symbol, 30));
        ctx.put("request", "Monitor all active trading algorithms and agent executions. " +
            "Analyze order flow for anomalies (volume, price, frequency), check fill quality, " +
            "assess algorithm health, and recommend circuit breaker action if needed. " +
            "Report execution quality metrics and position snapshot.");
        return ctx;
    }
}
