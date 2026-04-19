package com.QuantPlatformApplication.QuantPlatformApplication.service;

import com.QuantPlatformApplication.QuantPlatformApplication.engine.model.RiskCheckResult;
import com.QuantPlatformApplication.QuantPlatformApplication.engine.model.TradeSignal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Routes approved trades based on execution mode.
 * AUTONOMOUS: Execute immediately via broker adapter.
 * HUMAN_IN_LOOP: Send to Telegram for approval, hold until approved/rejected/expired.
 */
@Slf4j
@Component
public class ExecutionModeRouter {

    /**
     * Route an approved trade signal to the appropriate execution path.
     */
    public void route(TradeSignal signal, RiskCheckResult riskResult, String executionMode) {
        switch (executionMode) {
            case "AUTONOMOUS" -> executeAutonomous(signal, riskResult);
            case "HUMAN_IN_LOOP" -> holdForApproval(signal, riskResult);
            default -> {
                log.warn("Unknown execution mode: {}. Defaulting to HUMAN_IN_LOOP", executionMode);
                holdForApproval(signal, riskResult);
            }
        }
    }

    private void executeAutonomous(TradeSignal signal, RiskCheckResult riskResult) {
        log.info("AUTO-EXECUTE: {} {} @ {} | Size: {} | EffLev: {}x",
            signal.getAction(), signal.getSymbol(), signal.getEntryPrice(),
            riskResult.getPositionSize(), riskResult.getEffectiveLeverage());
        // TODO: Phase 3 will wire this to the actual broker adapter + Telegram notification
    }

    private void holdForApproval(TradeSignal signal, RiskCheckResult riskResult) {
        log.info("HOLD-FOR-APPROVAL: {} {} @ {} | Size: {} | Awaiting Telegram /approve",
            signal.getAction(), signal.getSymbol(), signal.getEntryPrice(),
            riskResult.getPositionSize());
        // TODO: Phase 3 will wire this to Telegram bot for approval/rejection
    }
}
