package com.QuantPlatformApplication.QuantPlatformApplication.service;

import com.QuantPlatformApplication.QuantPlatformApplication.engine.model.*;
import com.QuantPlatformApplication.QuantPlatformApplication.engine.strategy.MultiTimeFrameStrategy;
import com.QuantPlatformApplication.QuantPlatformApplication.service.risk.TradeRiskEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Central orchestration pipeline:
 * MultiTimeFrameData -> Strategy analysis -> Risk engine -> Execution routing
 *
 * All registered MultiTimeFrameStrategy implementations are evaluated.
 * Signals that pass risk checks are routed based on execution mode.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StrategyOrchestrator {

    private final List<MultiTimeFrameStrategy> strategies;
    private final TradeRiskEngine riskEngine;
    private final ExecutionModeRouter executionRouter;

    /**
     * Run all strategies against the given market data.
     * Each strategy produces 0 or 1 signal. Signals are independently risk-checked.
     */
    public void evaluateStrategies(MultiTimeFrameData data,
                                    double currentBalance, double peakEquity,
                                    double currentExposure, double dailyRealizedLoss,
                                    Set<String> openPositionSymbols,
                                    RiskParameters riskParams,
                                    String executionMode) {

        for (MultiTimeFrameStrategy strategy : strategies) {
            try {
                Optional<TradeSignal> signal = strategy.analyze(data);

                if (signal.isEmpty()) {
                    log.debug("{}: No signal for {}", strategy.getName(), data.getSymbol());
                    continue;
                }

                TradeSignal tradeSignal = signal.get();
                log.info("{}: {} signal for {} @ {} (confidence: {})",
                    strategy.getName(), tradeSignal.getAction(),
                    tradeSignal.getSymbol(), tradeSignal.getEntryPrice(),
                    tradeSignal.getConfidence());

                // Run through risk engine
                TradeRequest riskRequest = tradeSignal.toTradeRequest();
                RiskCheckResult riskResult = riskEngine.evaluate(
                    riskRequest, currentBalance, peakEquity,
                    currentExposure, dailyRealizedLoss,
                    openPositionSymbols, riskParams
                );

                if (!riskResult.isApproved()) {
                    log.warn("{}: Trade REJECTED by risk engine: {}",
                        strategy.getName(), riskResult.getRejectionReasons());
                    continue;
                }

                // Route based on execution mode
                executionRouter.route(tradeSignal, riskResult, executionMode);

                // Update exposure tracking for subsequent strategies in this cycle
                currentExposure += riskResult.getPositionSize() * tradeSignal.getEntryPrice();
                openPositionSymbols = new java.util.HashSet<>(openPositionSymbols);
                openPositionSymbols.add(tradeSignal.getSymbol());

            } catch (Exception e) {
                log.error("Strategy {} failed: {}", strategy.getName(), e.getMessage(), e);
            }
        }
    }
}
