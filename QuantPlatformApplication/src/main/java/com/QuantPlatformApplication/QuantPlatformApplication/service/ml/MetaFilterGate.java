package com.QuantPlatformApplication.QuantPlatformApplication.service.ml;

import com.QuantPlatformApplication.QuantPlatformApplication.client.MLMetaClient;
import com.QuantPlatformApplication.QuantPlatformApplication.client.MLMetaPredictionResponse;
import com.QuantPlatformApplication.QuantPlatformApplication.service.telegram.TelegramBotService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Production-side wrapper around MLMetaClient for live paper trading.
 *
 * Policy (per user decision 2026-05-12):
 *   - ml-service returns meta_prob >= threshold: allow
 *   - ml-service returns meta_prob <  threshold: veto (routine, silent)
 *   - ml-service throws / times out: FAIL OPEN (allow trade) + loud Telegram alert
 *
 * Rationale: the rules-based strategies existed before the meta filter and were
 * acceptable on their own. An ml-service outage must not halt all trading, but
 * the operator must see it immediately.
 */
@Slf4j
@Component
public class MetaFilterGate {

    private final MLMetaClient client;
    private final TelegramBotService telegram;
    private final double defaultThreshold;

    public MetaFilterGate(
            MLMetaClient client,
            TelegramBotService telegram,
            @Value("${quantedge.meta.threshold:0.55}") double defaultThreshold) {
        this.client = client;
        this.telegram = telegram;
        this.defaultThreshold = defaultThreshold;
    }

    public Decision check(String symbol, String direction,
                          double entryPrice, double tpPct, double slPct) {
        return checkWithThreshold(symbol, direction, entryPrice, tpPct, slPct, defaultThreshold);
    }

    public Decision checkWithThreshold(String symbol, String direction,
                                       double entryPrice, double tpPct, double slPct,
                                       double threshold) {
        try {
            MLMetaPredictionResponse resp = client.predictMeta(
                symbol, direction, entryPrice, tpPct, slPct);
            boolean allow = resp.metaProb() >= threshold;
            String reason = allow
                ? String.format("meta_prob=%.3f >= threshold=%.2f", resp.metaProb(), threshold)
                : String.format("meta_prob=%.3f below threshold=%.2f", resp.metaProb(), threshold);
            return new Decision(allow, resp.metaProb(), reason, false);
        } catch (Exception e) {
            log.warn("MetaFilterGate FAIL-OPEN for {} {} @ {}: {}",
                symbol, direction, entryPrice, e.getMessage());
            telegram.sendMessage(String.format(
                "🚨 *ML Meta-Filter Unreachable*%n%n" +
                "Symbol: %s %s @ $%.2f%nError: %s%n" +
                "Policy: FAIL OPEN — trade allowed without ML veto.",
                symbol, direction, entryPrice, e.getMessage()));
            return new Decision(true, Double.NaN, "fail-open: " + e.getMessage(), true);
        }
    }

    public record Decision(boolean allow, double metaProb, String reason, boolean failedOpen) {}
}
