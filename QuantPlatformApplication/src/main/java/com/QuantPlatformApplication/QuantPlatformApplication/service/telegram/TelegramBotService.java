package com.QuantPlatformApplication.QuantPlatformApplication.service.telegram;

import com.QuantPlatformApplication.QuantPlatformApplication.engine.model.RiskCheckResult;
import com.QuantPlatformApplication.QuantPlatformApplication.engine.model.TradeSignal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;
import java.util.Set;

@Slf4j
@Service
public class TelegramBotService {

    private static final Set<String> VALID_COMMANDS = Set.of(
        "status", "positions", "approve", "reject", "stop", "resume",
        "close_all", "close", "mode", "risk", "today", "explain"
    );

    private final TelegramBotConfig config;
    private final WebClient webClient;

    public TelegramBotService(TelegramBotConfig config) {
        this.config = config;
        this.webClient = WebClient.builder()
            .baseUrl(config.getApiBaseUrl())
            .build();
    }

    // --- Message Formatting ---

    public String formatTradeSignal(TradeSignal signal, RiskCheckResult risk) {
        String direction = signal.getAction().name();
        return String.format(
            "\uD83D\uDD14 *Trade Signal \u2014 %s*\n\n" +
            "*%s %s* @ $%.2f\n" +
            "Stop: $%.2f | TP: $%.2f\n" +
            "Risk: $%.2f (%.1f%%) | Eff. Leverage: %.1fx\n" +
            "Strategy: %s | Confidence: %.0f%%\n\n" +
            "*Why:* %s\n" +
            "*Entry:* %s\n\n" +
            "Reply /approve or /reject (expires in %ds)",
            signal.getSymbol(),
            direction, signal.getSymbol(), signal.getEntryPrice(),
            signal.getStopLossPrice(), signal.getTakeProfitPrice(),
            risk.getRiskAmount(), risk.getRiskAmount() / risk.getPositionSize() * 100,
            risk.getEffectiveLeverage(),
            signal.getStrategyName(), signal.getConfidence() * 100,
            signal.getBiasExplanation() != null ? signal.getBiasExplanation() : "N/A",
            signal.getTriggerExplanation() != null ? signal.getTriggerExplanation() : "N/A",
            config.getSignalTimeoutSeconds()
        );
    }

    public String formatTradeExecuted(TradeSignal signal, RiskCheckResult risk) {
        return String.format(
            "\u2705 *Trade Executed*\n\n" +
            "*%s %s* filled @ $%.2f\n" +
            "Stop: $%.2f | TP: $%.2f\n" +
            "Size: %.6f | Eff. Leverage: %.1fx",
            signal.getAction().name(), signal.getSymbol(), signal.getEntryPrice(),
            signal.getStopLossPrice(), signal.getTakeProfitPrice(),
            risk.getPositionSize(), risk.getEffectiveLeverage()
        );
    }

    public String formatDailySummary(double equity, double startEquity,
                                      int wins, int losses, double winRate) {
        double pnl = equity - startEquity;
        String emoji = pnl >= 0 ? "\uD83D\uDCC8" : "\uD83D\uDCC9";
        return String.format(
            "%s *Daily Summary*\n\n" +
            "Equity: $%.2f (%+.2f)\n" +
            "Trades: %dW / %dL (%.0f%% win rate)\n" +
            "P&L: $%+.2f",
            emoji, equity, pnl, wins, losses, winRate, pnl
        );
    }

    public String formatRiskAlert(String alertType, String message) {
        return String.format(
            "\uD83D\uDEA8 *Risk Alert: %s*\n\n%s",
            alertType, message
        );
    }

    public String formatCriticalHalt(double drawdownPct, double currentEquity) {
        return String.format(
            "\uD83D\uDD34 *CRITICAL: MAX DRAWDOWN REACHED*\n\n" +
            "Drawdown: %.1f%%\n" +
            "Current Equity: $%.2f\n" +
            "All positions closed. Trading halted.\n" +
            "Manual restart required.",
            drawdownPct, currentEquity
        );
    }

    // --- Command Parsing ---

    public String parseCommand(String text) {
        if (text == null || text.isEmpty() || !text.startsWith("/")) {
            return null;
        }
        String command = text.split("\\s+")[0].substring(1).toLowerCase();
        return VALID_COMMANDS.contains(command) ? command : null;
    }

    // --- Send Messages ---

    public void sendMessage(String text) {
        if (!config.isEnabled() || config.getBotToken().isEmpty()) {
            log.debug("Telegram disabled or not configured. Message: {}", text);
            return;
        }

        String url = String.format("/bot%s/sendMessage", config.getBotToken());

        webClient.post()
            .uri(url)
            .bodyValue(Map.of(
                "chat_id", config.getChatId(),
                "text", text,
                "parse_mode", "Markdown"
            ))
            .retrieve()
            .bodyToMono(String.class)
            .doOnError(e -> log.error("Failed to send Telegram message: {}", e.getMessage()))
            .subscribe(
                response -> log.debug("Telegram message sent"),
                error -> log.error("Telegram send failed: {}", error.getMessage())
            );
    }
}
