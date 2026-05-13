package com.QuantPlatformApplication.QuantPlatformApplication.service.paper;

import com.QuantPlatformApplication.QuantPlatformApplication.engine.model.Action;
import com.QuantPlatformApplication.QuantPlatformApplication.engine.model.RiskCheckResult;
import com.QuantPlatformApplication.QuantPlatformApplication.engine.model.TradeSignal;
import com.QuantPlatformApplication.QuantPlatformApplication.model.entity.TradeLog;
import com.QuantPlatformApplication.QuantPlatformApplication.repository.TradeLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Persists paper-trading entries to TradeLog with Learn-While-Earning
 * explanation metadata (bias, trigger, meta-filter probability, risk).
 *
 * userId=0 is reserved for the system paper-trading account until
 * multi-tenant SaaS is scoped (out of scope for Plan 4).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaperTradePersister {

    private static final long SYSTEM_PAPER_USER_ID = 0L;

    private final TradeLogRepository tradeLogRepo;

    public Long persist(TradeSignal signal, RiskCheckResult risk, double metaProb) {
        String direction = signal.getAction() == Action.BUY ? "LONG" : "SHORT";
        Map<String, Object> explanation = new HashMap<>();
        explanation.put("bias", signal.getBiasExplanation());
        explanation.put("trigger", signal.getTriggerExplanation());
        explanation.put("confidence", signal.getConfidence());
        explanation.put("meta_prob", Double.isNaN(metaProb) ? null : metaProb);
        explanation.put("risk_amount", risk.getRiskAmount());
        explanation.put("effective_leverage", risk.getEffectiveLeverage());
        explanation.put("nominal_leverage", risk.getNominalLeverage());

        TradeLog tl = TradeLog.builder()
            .userId(SYSTEM_PAPER_USER_ID)
            .tradeId("paper-" + UUID.randomUUID())
            .symbol(signal.getSymbol())
            .direction(direction)
            .strategyName(signal.getStrategyName())
            .entryPrice(BigDecimal.valueOf(signal.getEntryPrice()))
            .stopLossPrice(BigDecimal.valueOf(signal.getStopLossPrice()))
            .takeProfitPrice(BigDecimal.valueOf(signal.getTakeProfitPrice()))
            .positionSize(BigDecimal.valueOf(risk.getPositionSize()))
            .effectiveLeverage(BigDecimal.valueOf(risk.getEffectiveLeverage()))
            .confidence(BigDecimal.valueOf(signal.getConfidence()))
            .explanation(explanation)
            .status("OPEN")
            .executionMode("AUTONOMOUS")
            .build();

        TradeLog saved = tradeLogRepo.save(tl);
        log.info("Paper trade persisted: id={} tradeId={} {} {} @ {}",
            saved.getId(), saved.getTradeId(), saved.getSymbol(), direction, signal.getEntryPrice());
        return saved.getId();
    }

    public void markClosed(String tradeId, double exitPrice, String outcome, double realizedPnl) {
        tradeLogRepo.findByTradeId(tradeId).ifPresentOrElse(
            tl -> {
                Map<String, Object> out = new HashMap<>();
                out.put("exit_price", exitPrice);
                out.put("outcome", outcome); // "TP", "SL", "MANUAL"
                out.put("realized_pnl", realizedPnl);
                tl.setOutcome(out);
                tl.setStatus("CLOSED");
                tl.setClosedAt(java.time.Instant.now());
                tradeLogRepo.save(tl);
            },
            () -> log.warn("markClosed: tradeId not found: {}", tradeId)
        );
    }
}
