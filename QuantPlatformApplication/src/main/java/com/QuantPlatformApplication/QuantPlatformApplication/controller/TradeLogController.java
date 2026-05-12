package com.QuantPlatformApplication.QuantPlatformApplication.controller;

import com.QuantPlatformApplication.QuantPlatformApplication.model.entity.TradeLog;
import com.QuantPlatformApplication.QuantPlatformApplication.repository.TradeLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Read-only TradeLog endpoints for the frontend TradeLogPage.
 *
 * Normalizes backend fields (direction LONG/SHORT, outcome.outcome TP/SL/MANUAL)
 * into the shape the frontend expects (direction BUY/SELL, outcome.result WIN/LOSS).
 * This keeps the frontend type stable across Plan 4's paper-trading additions
 * without forcing a UI refactor.
 *
 * Returns paper-trading user (userId=0) trades by default, matching
 * PaperTradePersister's convention.
 */
@RestController
@RequestMapping("/api/v1/trade-logs")
@RequiredArgsConstructor
public class TradeLogController {

    private static final long SYSTEM_PAPER_USER_ID = 0L;

    private final TradeLogRepository tradeLogRepo;

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> list() {
        return ResponseEntity.ok(
            tradeLogRepo.findByUserIdOrderByCreatedAtDesc(SYSTEM_PAPER_USER_ID).stream()
                .map(this::toDto)
                .toList()
        );
    }

    @GetMapping("/{tradeId}")
    public ResponseEntity<?> get(@PathVariable String tradeId) {
        return tradeLogRepo.findByTradeId(tradeId)
            .<ResponseEntity<?>>map(tl -> ResponseEntity.ok(toDto(tl)))
            .orElse(ResponseEntity.notFound().build());
    }

    private Map<String, Object> toDto(TradeLog tl) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", tl.getId());
        dto.put("userId", tl.getUserId());
        dto.put("tradeId", tl.getTradeId());
        dto.put("symbol", tl.getSymbol());
        dto.put("direction", toFrontendDirection(tl.getDirection()));
        dto.put("strategyName", tl.getStrategyName());
        dto.put("entryPrice", bdToDouble(tl.getEntryPrice()));
        dto.put("stopLossPrice", bdToDouble(tl.getStopLossPrice()));
        dto.put("takeProfitPrice", bdToDouble(tl.getTakeProfitPrice()));
        dto.put("positionSize", bdToDouble(tl.getPositionSize()));
        dto.put("effectiveLeverage", bdToDouble(tl.getEffectiveLeverage()));
        dto.put("confidence", bdToDouble(tl.getConfidence()));
        dto.put("explanation", tl.getExplanation());
        dto.put("outcome", toFrontendOutcome(tl));
        dto.put("status", tl.getStatus());
        dto.put("executionMode", tl.getExecutionMode());
        dto.put("openedAt", tl.getOpenedAt());
        dto.put("closedAt", tl.getClosedAt());
        dto.put("createdAt", tl.getCreatedAt());
        dto.put("updatedAt", tl.getUpdatedAt());
        return dto;
    }

    private String toFrontendDirection(String d) {
        if ("LONG".equals(d)) return "BUY";
        if ("SHORT".equals(d)) return "SELL";
        return d;
    }

    private Map<String, Object> toFrontendOutcome(TradeLog tl) {
        Map<String, Object> raw = tl.getOutcome();
        if (raw == null || raw.isEmpty()) return null;

        Map<String, Object> out = new LinkedHashMap<>();
        Object result = raw.get("outcome");
        if ("TP".equals(result)) out.put("result", "WIN");
        else if ("SL".equals(result)) out.put("result", "LOSS");
        else if (result != null) out.put("result", result);

        Object pnl = raw.get("realized_pnl");
        if (pnl instanceof Number pnlN) {
            double entry = bdToDouble(tl.getEntryPrice());
            double risk = Math.abs(entry - bdToDouble(tl.getStopLossPrice())) * bdToDouble(tl.getPositionSize());
            out.put("pnl", pnlN.doubleValue());
            out.put("rMultiple", risk > 0 ? pnlN.doubleValue() / risk : 0.0);
        }
        Object exit = raw.get("exit_price");
        if (exit instanceof Number exitN) out.put("exitPrice", exitN.doubleValue());
        return out;
    }

    private double bdToDouble(BigDecimal bd) {
        return bd == null ? 0.0 : bd.doubleValue();
    }
}
