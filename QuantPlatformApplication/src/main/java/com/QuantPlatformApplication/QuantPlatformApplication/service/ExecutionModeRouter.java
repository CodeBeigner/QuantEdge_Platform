package com.QuantPlatformApplication.QuantPlatformApplication.service;

import com.QuantPlatformApplication.QuantPlatformApplication.engine.model.Action;
import com.QuantPlatformApplication.QuantPlatformApplication.engine.model.RiskCheckResult;
import com.QuantPlatformApplication.QuantPlatformApplication.engine.model.TradeSignal;
import com.QuantPlatformApplication.QuantPlatformApplication.service.broker.PaperBrokerAdapter;
import com.QuantPlatformApplication.QuantPlatformApplication.service.paper.PaperTradePersister;
import com.QuantPlatformApplication.QuantPlatformApplication.service.telegram.TelegramBotService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Routes approved trades based on execution mode.
 *
 * AUTONOMOUS: immediately place via PaperBrokerAdapter, persist to TradeLog,
 *             fire Telegram alert.
 * HUMAN_IN_LOOP: send Telegram alert for visibility. Two-way /approve
 *                commands are deliberately out of scope for Plan 4 (user
 *                decision 2026-05-12); treated as alert-only for now.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExecutionModeRouter {

    private final PaperBrokerAdapter paperBroker;
    private final PaperTradePersister tradePersister;
    private final TelegramBotService telegram;

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

        try {
            String side = signal.getAction() == Action.BUY ? "BUY" : "SELL";
            Map<String, Object> placed = paperBroker.placeOrder(
                signal.getSymbol(), side, "MARKET",
                riskResult.getPositionSize(), signal.getEntryPrice());

            // Meta-prob is not carried on TradeSignal today; MarketTickScheduler
            // passes NaN when the gate isn't engaged. Routing layer records
            // NaN explicitly rather than faking a value.
            Long tradeLogId = tradePersister.persist(signal, riskResult, Double.NaN);

            telegram.sendMessage(telegram.formatTradeExecuted(signal, riskResult));

            log.info("Paper fill persisted: tradeLog={} broker={}", tradeLogId, placed.get("orderId"));
        } catch (Exception e) {
            log.error("Paper execution failed for {} {}: {}",
                signal.getAction(), signal.getSymbol(), e.getMessage(), e);
            telegram.sendMessage(telegram.formatRiskAlert(
                "Paper Execution Failed",
                signal.getSymbol() + " " + signal.getAction() + ": " + e.getMessage()));
        }
    }

    private void holdForApproval(TradeSignal signal, RiskCheckResult riskResult) {
        log.info("HOLD-FOR-APPROVAL: {} {} @ {} | Size: {} | (Telegram 2-way deferred)",
            signal.getAction(), signal.getSymbol(), signal.getEntryPrice(),
            riskResult.getPositionSize());
        telegram.sendMessage(telegram.formatTradeSignal(signal, riskResult));
    }
}
