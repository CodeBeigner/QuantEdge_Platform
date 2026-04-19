package com.QuantPlatformApplication.QuantPlatformApplication.service.telegram;

import com.QuantPlatformApplication.QuantPlatformApplication.engine.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TelegramBotServiceTest {

    private TelegramBotService service;

    @BeforeEach
    void setUp() {
        TelegramBotConfig config = new TelegramBotConfig();
        config.setBotToken("test-token");
        config.setChatId("123456");
        config.setEnabled(true);
        service = new TelegramBotService(config);
    }

    private TradeSignal buildSignal(Action action, double entry, double stop, double tp) {
        return TradeSignal.builder()
            .symbol("BTCUSD").action(action)
            .entryPrice(entry).stopLossPrice(stop).takeProfitPrice(tp)
            .confidence(0.75).strategyName("Trend Continuation")
            .biasExplanation("4H bullish").triggerExplanation("15M engulfing")
            .lesson("Trend continuation setup").metadata(Map.of())
            .build();
    }

    @Test
    void formatTradeSignalForApproval() {
        TradeSignal signal = buildSignal(Action.BUY, 67000, 66650, 67525);
        RiskCheckResult risk = RiskCheckResult.approve(0.015, 5.0, 2.2, 10);

        String message = service.formatTradeSignal(signal, risk);

        assertTrue(message.contains("BTCUSD"));
        assertTrue(message.contains("BUY") || message.contains("LONG"));
        assertTrue(message.contains("67000") || message.contains("67,000"));
        assertTrue(message.contains("/approve") || message.contains("approve"));
        assertTrue(message.contains("/reject") || message.contains("reject"));
    }

    @Test
    void formatTradeExecuted() {
        TradeSignal signal = buildSignal(Action.BUY, 67000, 66650, 67525);
        RiskCheckResult risk = RiskCheckResult.approve(0.015, 5.0, 2.2, 10);

        String message = service.formatTradeExecuted(signal, risk);

        assertTrue(message.contains("BTCUSD"));
        assertTrue(message.contains("67000") || message.contains("67,000"));
    }

    @Test
    void formatDailySummary() {
        String message = service.formatDailySummary(518.50, 500.0, 3, 1, 75.0);

        assertTrue(message.contains("518"));
        assertTrue(message.contains("3")); // wins
    }

    @Test
    void formatRiskAlert() {
        String message = service.formatRiskAlert("DAILY_LOSS_LIMIT",
            "Daily loss $25 hit 5% limit. Trading halted.");

        assertTrue(message.contains("DAILY_LOSS_LIMIT") || message.contains("daily loss"));
        assertTrue(message.contains("halted") || message.contains("HALT"));
    }

    @Test
    void formatCriticalHalt() {
        String message = service.formatCriticalHalt(15.2, 424.0);

        assertTrue(message.contains("15") || message.contains("drawdown"));
        assertTrue(message.contains("424") || message.contains("equity"));
    }

    @Test
    void parseCommandExtractsAction() {
        assertEquals("status", service.parseCommand("/status"));
        assertEquals("approve", service.parseCommand("/approve"));
        assertEquals("reject", service.parseCommand("/reject"));
        assertEquals("stop", service.parseCommand("/stop"));
        assertEquals("resume", service.parseCommand("/resume"));
        assertEquals("close_all", service.parseCommand("/close_all"));
        assertNull(service.parseCommand("random text"));
        assertNull(service.parseCommand(""));
    }
}
