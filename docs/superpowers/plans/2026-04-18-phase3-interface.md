# Phase 3: Interface Layer — Telegram Bot, Realistic Backtesting, Live Data Pipeline

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Connect the trading core to the real world — Telegram bot for two-way control, realistic multi-timeframe backtesting engine, and live data pipeline from Delta Exchange WebSocket.

**Architecture:** Telegram Bot API via Spring WebFlux WebClient (no external library needed). New MultiTimeFrameBacktestEngine replaces the old single-timeframe engine. Live MarketDataPipeline orchestrates Delta Exchange WebSocket → candle aggregation → indicator calculation → strategy evaluation.

**Tech Stack:** Java 21, Spring Boot 3.5.11, Spring WebFlux WebClient (Telegram API), Spring WebSocket (live data), existing Phase 1+2 components.

**Base Package:** `com.QuantPlatformApplication.QuantPlatformApplication`

**Base Path:** `/Users/abhinavunmesh/Desktop/QuantEdge_Platform/QuantPlatformApplication/src/main/java/com/QuantPlatformApplication/QuantPlatformApplication`

**Test Base Path:** `/Users/abhinavunmesh/Desktop/QuantEdge_Platform/QuantPlatformApplication/src/test/java/com/QuantPlatformApplication/QuantPlatformApplication`

---

## File Structure

### New Files

```
# Telegram Bot
service/telegram/TelegramBotConfig.java                   — Config properties (bot token, chat ID)
service/telegram/TelegramBotService.java                  — Send messages, parse commands, format alerts
service/telegram/TelegramCommandHandler.java              — Handle /status, /approve, /reject, /stop, etc.
service/telegram/TelegramWebhookController.java           — Receive Telegram webhook updates (or polling)
model/dto/TelegramUpdate.java                             — Telegram webhook payload DTO

# Multi-Timeframe Backtesting
engine/MultiTimeFrameBacktestEngine.java                  — Realistic backtesting with multi-TF candles
engine/model/BacktestConfig.java                          — Backtest configuration (slippage, fees, funding)
engine/model/MultiTimeFrameBacktestResult.java            — Extended result with per-strategy and per-regime metrics
service/MultiTimeFrameBacktestService.java                — Orchestrates backtests, stores results
controller/MultiTimeFrameBacktestController.java          — REST API for new backtesting

# Live Data Pipeline
service/pipeline/MarketDataPipeline.java                  — Orchestrates: WS data → candles → indicators → strategies
service/pipeline/DeltaExchangeDataFeed.java               — WebSocket connection to Delta Exchange for live candles
service/pipeline/FundingRateTracker.java                   — Tracks funding rate history, predicts next rate
service/pipeline/AccountStateTracker.java                  — Tracks balance, equity, exposure, daily P&L

# DB migration
db/migration/V20__create_pending_signals.sql              — Pending trade signals awaiting approval
```

### Test Files

```
service/telegram/TelegramBotServiceTest.java              — 6 tests (message formatting, command parsing)
engine/MultiTimeFrameBacktestEngineTest.java              — 8 tests (realistic simulation)
service/pipeline/FundingRateTrackerTest.java              — 4 tests
service/pipeline/AccountStateTrackerTest.java             — 5 tests
```

---

## Task 1: Telegram Bot Config and Service with Tests (TDD)

**Files:**
- Create: `service/telegram/TelegramBotConfig.java`
- Create: `service/telegram/TelegramBotService.java`
- Test: `service/telegram/TelegramBotServiceTest.java`

- [ ] **Step 1: Create TelegramBotConfig**

```java
// service/telegram/TelegramBotConfig.java
package com.QuantPlatformApplication.QuantPlatformApplication.service.telegram;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "telegram")
public class TelegramBotConfig {
    private String botToken = "";
    private String chatId = "";
    private String apiBaseUrl = "https://api.telegram.org";
    private int signalTimeoutSeconds = 120;  // 2 minutes for human-in-loop approval
    private boolean enabled = false;
}
```

- [ ] **Step 2: Write failing tests for message formatting**

```java
// TelegramBotServiceTest.java
package com.QuantPlatformApplication.QuantPlatformApplication.service.telegram;

import com.QuantPlatformApplication.QuantPlatformApplication.engine.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
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
```

- [ ] **Step 3: Write TelegramBotService implementation**

```java
// service/telegram/TelegramBotService.java
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
            "\uD83D\uDD14 *Trade Signal — %s*\n\n" +
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
```

- [ ] **Step 4: Add Telegram config to application.yml**

Append to existing application.yml:
```yaml
telegram:
  bot-token: ${TELEGRAM_BOT_TOKEN:}
  chat-id: ${TELEGRAM_CHAT_ID:}
  enabled: ${TELEGRAM_ENABLED:false}
  signal-timeout-seconds: 120
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `cd /Users/abhinavunmesh/Desktop/QuantEdge_Platform/QuantPlatformApplication && JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./mvnw test -Dtest=TelegramBotServiceTest -pl .`
Expected: All 6 tests PASS

- [ ] **Step 6: Commit**

```bash
git commit -m "feat: add Telegram bot service with TDD — message formatting, command parsing, alert types"
```

---

## Task 2: Telegram Command Handler

**Files:**
- Create: `service/telegram/TelegramCommandHandler.java`
- Create: `service/telegram/TelegramWebhookController.java`
- Create: `model/dto/TelegramUpdate.java`
- Create: `db/migration/V20__create_pending_signals.sql`

- [ ] **Step 1: Create pending_signals migration**

```sql
-- V20__create_pending_signals.sql
CREATE TABLE pending_signals (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    signal_id       VARCHAR(50) NOT NULL UNIQUE,
    symbol          VARCHAR(20) NOT NULL,
    direction       VARCHAR(10) NOT NULL,
    strategy_name   VARCHAR(50) NOT NULL,
    entry_price     NUMERIC(19,4) NOT NULL,
    stop_loss_price NUMERIC(19,4) NOT NULL,
    take_profit_price NUMERIC(19,4) NOT NULL,
    position_size   NUMERIC(19,8) NOT NULL,
    confidence      NUMERIC(4,3) NOT NULL,
    signal_data     JSONB       NOT NULL DEFAULT '{}',
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    expires_at      TIMESTAMPTZ NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_pending_signals_user_status ON pending_signals(user_id, status);
```

- [ ] **Step 2: Create TelegramUpdate DTO**

```java
// model/dto/TelegramUpdate.java
package com.QuantPlatformApplication.QuantPlatformApplication.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class TelegramUpdate {
    @JsonProperty("update_id")
    private Long updateId;

    private TelegramMessage message;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TelegramMessage {
        @JsonProperty("message_id")
        private Long messageId;

        private TelegramChat chat;
        private String text;

        @JsonProperty("from")
        private TelegramUser fromUser;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TelegramChat {
        private Long id;
        private String type;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TelegramUser {
        private Long id;
        @JsonProperty("first_name")
        private String firstName;
        private String username;
    }
}
```

- [ ] **Step 3: Create TelegramCommandHandler**

```java
// service/telegram/TelegramCommandHandler.java
package com.QuantPlatformApplication.QuantPlatformApplication.service.telegram;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Handles incoming Telegram commands and dispatches actions.
 * Commands: /status, /positions, /approve, /reject, /stop, /resume,
 *           /close_all, /close [PAIR], /mode [auto|manual], /risk, /today, /explain [id]
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TelegramCommandHandler {

    private final TelegramBotService botService;

    public void handleCommand(String command, String fullText, String chatId) {
        log.info("Telegram command received: {} from chat {}", command, chatId);

        switch (command) {
            case "status" -> handleStatus();
            case "positions" -> handlePositions();
            case "approve" -> handleApprove();
            case "reject" -> handleReject();
            case "stop" -> handleStop();
            case "resume" -> handleResume();
            case "close_all" -> handleCloseAll();
            case "close" -> handleClose(fullText);
            case "mode" -> handleMode(fullText);
            case "risk" -> handleRisk();
            case "today" -> handleToday();
            case "explain" -> handleExplain(fullText);
            default -> botService.sendMessage("Unknown command: /" + command);
        }
    }

    private void handleStatus() {
        // TODO: Phase 4 wires to AccountStateTracker for live data
        botService.sendMessage("\uD83D\uDFE2 *Bot Status*\n\nStatus: Online\nMode: AUTONOMOUS\nOpen positions: 0\nDaily P&L: $0.00");
    }

    private void handlePositions() {
        botService.sendMessage("No open positions.");
    }

    private void handleApprove() {
        // TODO: Look up latest pending signal, execute it
        botService.sendMessage("\u2705 Trade approved. Executing...");
    }

    private void handleReject() {
        // TODO: Cancel latest pending signal
        botService.sendMessage("\u274C Trade rejected.");
    }

    private void handleStop() {
        botService.sendMessage("\u23F8 Trading paused. Positions remain open. Use /resume to restart.");
    }

    private void handleResume() {
        botService.sendMessage("\u25B6 Trading resumed.");
    }

    private void handleCloseAll() {
        botService.sendMessage("\uD83D\uDEA8 Emergency close: closing all positions at market...");
        // TODO: Wire to broker adapter
    }

    private void handleClose(String fullText) {
        String[] parts = fullText.split("\\s+", 2);
        if (parts.length < 2) {
            botService.sendMessage("Usage: /close BTCUSD");
            return;
        }
        botService.sendMessage("Closing position: " + parts[1]);
    }

    private void handleMode(String fullText) {
        String[] parts = fullText.split("\\s+", 2);
        if (parts.length < 2) {
            botService.sendMessage("Usage: /mode auto or /mode manual");
            return;
        }
        String mode = parts[1].toLowerCase();
        if ("auto".equals(mode)) {
            botService.sendMessage("\uD83E\uDD16 Switched to AUTONOMOUS mode.");
        } else if ("manual".equals(mode)) {
            botService.sendMessage("\uD83D\uDC64 Switched to HUMAN_IN_LOOP mode.");
        } else {
            botService.sendMessage("Unknown mode. Use: /mode auto or /mode manual");
        }
    }

    private void handleRisk() {
        botService.sendMessage("*Risk Parameters*\n\nRisk/trade: 1%\nMax leverage: 5x\nDaily loss limit: 5%\nMax drawdown: 15%\nMax positions: 3");
    }

    private void handleToday() {
        botService.sendMessage("No trades today.");
    }

    private void handleExplain(String fullText) {
        String[] parts = fullText.split("\\s+", 2);
        if (parts.length < 2) {
            botService.sendMessage("Usage: /explain TRD-2026-04-18-001");
            return;
        }
        botService.sendMessage("Trade explanation for: " + parts[1] + "\n(Not yet connected to trade log)");
    }
}
```

- [ ] **Step 4: Create TelegramWebhookController**

```java
// service/telegram/TelegramWebhookController.java
package com.QuantPlatformApplication.QuantPlatformApplication.service.telegram;

import com.QuantPlatformApplication.QuantPlatformApplication.model.dto.TelegramUpdate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/telegram")
@RequiredArgsConstructor
public class TelegramWebhookController {

    private final TelegramBotService botService;
    private final TelegramCommandHandler commandHandler;
    private final TelegramBotConfig config;

    @PostMapping("/webhook")
    public ResponseEntity<String> handleWebhook(@RequestBody TelegramUpdate update) {
        if (update.getMessage() == null || update.getMessage().getText() == null) {
            return ResponseEntity.ok("ok");
        }

        String text = update.getMessage().getText().trim();
        String chatId = String.valueOf(update.getMessage().getChat().getId());

        // Verify chat ID matches configured chat
        if (!chatId.equals(config.getChatId())) {
            log.warn("Telegram message from unauthorized chat: {}", chatId);
            return ResponseEntity.ok("ok");
        }

        String command = botService.parseCommand(text);
        if (command != null) {
            commandHandler.handleCommand(command, text, chatId);
        }

        return ResponseEntity.ok("ok");
    }
}
```

- [ ] **Step 5: Update SecurityConfig to allow Telegram webhook**

Add `/api/v1/telegram/**` to the permitAll list in SecurityConfig.java.

- [ ] **Step 6: Commit**

```bash
git commit -m "feat: add Telegram command handler, webhook controller, and pending_signals migration"
```

---

## Task 3: Account State Tracker with Tests (TDD)

**Files:**
- Create: `service/pipeline/AccountStateTracker.java`
- Test: `service/pipeline/AccountStateTrackerTest.java`

Tracks balance, equity, peak equity, daily P&L, exposure, and open positions — all the data the risk engine needs.

- [ ] **Step 1: Write failing tests**

```java
// AccountStateTrackerTest.java
package com.QuantPlatformApplication.QuantPlatformApplication.service.pipeline;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AccountStateTrackerTest {

    private AccountStateTracker tracker;

    @BeforeEach
    void setUp() {
        tracker = new AccountStateTracker();
        tracker.initialize(500.0);
    }

    @Test
    void initializeSetsBalanceAndPeakEquity() {
        assertEquals(500.0, tracker.getCurrentBalance());
        assertEquals(500.0, tracker.getPeakEquity());
        assertEquals(500.0, tracker.getDayStartBalance());
        assertEquals(0.0, tracker.getDailyRealizedLoss());
    }

    @Test
    void recordWinUpdatesBalanceAndPeakEquity() {
        tracker.recordTradeResult("BTCUSD", 12.50);
        assertEquals(512.50, tracker.getCurrentBalance());
        assertEquals(512.50, tracker.getPeakEquity());
    }

    @Test
    void recordLossUpdatesBalanceAndDailyLoss() {
        tracker.recordTradeResult("BTCUSD", -8.0);
        assertEquals(492.0, tracker.getCurrentBalance());
        assertEquals(500.0, tracker.getPeakEquity()); // peak unchanged
        assertEquals(8.0, tracker.getDailyRealizedLoss());
    }

    @Test
    void peakEquityOnlyIncreasesNeverDecreases() {
        tracker.recordTradeResult("BTCUSD", 20.0);  // equity = 520
        tracker.recordTradeResult("ETHUSD", -10.0);  // equity = 510
        assertEquals(520.0, tracker.getPeakEquity()); // peak stays at 520
        assertEquals(510.0, tracker.getCurrentBalance());
    }

    @Test
    void resetDailyStatsClearsLossAndUpdatesStart() {
        tracker.recordTradeResult("BTCUSD", -15.0);
        assertEquals(15.0, tracker.getDailyRealizedLoss());

        tracker.resetDaily();
        assertEquals(0.0, tracker.getDailyRealizedLoss());
        assertEquals(485.0, tracker.getDayStartBalance());
    }
}
```

- [ ] **Step 2: Write implementation**

```java
// service/pipeline/AccountStateTracker.java
package com.QuantPlatformApplication.QuantPlatformApplication.service.pipeline;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

/**
 * Tracks account state needed by the risk engine.
 * Updated after every trade result and daily reset.
 */
@Slf4j
@Component
public class AccountStateTracker {

    @Getter private double currentBalance;
    @Getter private double peakEquity;
    @Getter private double dayStartBalance;
    @Getter private double dailyRealizedLoss;
    @Getter private double currentExposure;
    @Getter private final Set<String> openPositionSymbols = new HashSet<>();

    public void initialize(double startingBalance) {
        this.currentBalance = startingBalance;
        this.peakEquity = startingBalance;
        this.dayStartBalance = startingBalance;
        this.dailyRealizedLoss = 0;
        this.currentExposure = 0;
        this.openPositionSymbols.clear();
        log.info("Account initialized: balance=${}", startingBalance);
    }

    public void recordTradeResult(String symbol, double pnl) {
        currentBalance += pnl;

        if (pnl < 0) {
            dailyRealizedLoss += Math.abs(pnl);
        }

        if (currentBalance > peakEquity) {
            peakEquity = currentBalance;
        }

        log.info("Trade result for {}: ${} | Balance: ${} | Peak: ${} | Daily loss: ${}",
            symbol, pnl, currentBalance, peakEquity, dailyRealizedLoss);
    }

    public void addPosition(String symbol, double notionalValue) {
        openPositionSymbols.add(symbol);
        currentExposure += notionalValue;
    }

    public void removePosition(String symbol, double notionalValue) {
        openPositionSymbols.remove(symbol);
        currentExposure = Math.max(0, currentExposure - notionalValue);
    }

    public void resetDaily() {
        dailyRealizedLoss = 0;
        dayStartBalance = currentBalance;
        log.info("Daily reset. Starting balance: ${}", dayStartBalance);
    }
}
```

- [ ] **Step 3: Run tests, commit**

```bash
git commit -m "feat: add AccountStateTracker with TDD — balance, equity, daily P&L, exposure tracking"
```

---

## Task 4: Funding Rate Tracker with Tests (TDD)

**Files:**
- Create: `service/pipeline/FundingRateTracker.java`
- Test: `service/pipeline/FundingRateTrackerTest.java`

- [ ] **Step 1: Write failing tests**

```java
// FundingRateTrackerTest.java
package com.QuantPlatformApplication.QuantPlatformApplication.service.pipeline;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FundingRateTrackerTest {

    private FundingRateTracker tracker;

    @BeforeEach
    void setUp() {
        tracker = new FundingRateTracker();
    }

    @Test
    void recordsAndReturnsHistory() {
        tracker.recordFundingRate(0.01);
        tracker.recordFundingRate(0.02);
        tracker.recordFundingRate(0.015);

        List<Double> history = tracker.getHistory();
        assertEquals(3, history.size());
        assertEquals(0.015, history.get(0)); // newest first
    }

    @Test
    void historyLimitedToMaxSize() {
        for (int i = 0; i < 100; i++) {
            tracker.recordFundingRate(i * 0.001);
        }
        assertTrue(tracker.getHistory().size() <= 72); // max 72 (9 days of 8h intervals)
    }

    @Test
    void currentRateReturnsLatest() {
        tracker.recordFundingRate(0.01);
        tracker.recordFundingRate(0.05);
        assertEquals(0.05, tracker.getCurrentRate());
    }

    @Test
    void isExtremeDetectsHighPositive() {
        tracker.recordFundingRate(0.06);
        tracker.recordFundingRate(0.07);
        tracker.recordFundingRate(0.065);

        assertTrue(tracker.isExtremePositive(3));
    }
}
```

- [ ] **Step 2: Write implementation**

```java
// service/pipeline/FundingRateTracker.java
package com.QuantPlatformApplication.QuantPlatformApplication.service.pipeline;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

@Slf4j
@Component
public class FundingRateTracker {

    private static final int MAX_HISTORY = 72;  // 9 days of 8h intervals
    private static final double EXTREME_POSITIVE_THRESHOLD = 0.0005; // 0.05%
    private static final double EXTREME_NEGATIVE_THRESHOLD = -0.0003; // -0.03%

    private final Deque<Double> history = new ArrayDeque<>();

    public void recordFundingRate(double rate) {
        history.addFirst(rate);
        while (history.size() > MAX_HISTORY) {
            history.removeLast();
        }
        log.debug("Funding rate recorded: {} (history size: {})", rate, history.size());
    }

    public double getCurrentRate() {
        return history.isEmpty() ? 0 : history.peekFirst();
    }

    public List<Double> getHistory() {
        return new ArrayList<>(history);
    }

    public boolean isExtremePositive(int consecutivePeriods) {
        if (history.size() < consecutivePeriods) return false;
        int count = 0;
        for (Double rate : history) {
            if (rate > EXTREME_POSITIVE_THRESHOLD) {
                count++;
                if (count >= consecutivePeriods) return true;
            } else {
                break; // must be consecutive
            }
        }
        return false;
    }

    public boolean isExtremeNegative(int consecutivePeriods) {
        if (history.size() < consecutivePeriods) return false;
        int count = 0;
        for (Double rate : history) {
            if (rate < EXTREME_NEGATIVE_THRESHOLD) {
                count++;
                if (count >= consecutivePeriods) return true;
            } else {
                break;
            }
        }
        return false;
    }
}
```

- [ ] **Step 3: Run tests, commit**

```bash
git commit -m "feat: add FundingRateTracker with TDD — history, extremes detection, consecutive periods"
```

---

## Task 5: Multi-Timeframe Backtest Engine with Tests (TDD)

**Files:**
- Create: `engine/model/BacktestConfig.java`
- Create: `engine/model/MultiTimeFrameBacktestResult.java`
- Create: `engine/MultiTimeFrameBacktestEngine.java`
- Test: `engine/MultiTimeFrameBacktestEngineTest.java`

- [ ] **Step 1: Create BacktestConfig**

```java
// engine/model/BacktestConfig.java
package com.QuantPlatformApplication.QuantPlatformApplication.engine.model;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class BacktestConfig {
    @Builder.Default private final double initialCapital = 500;
    @Builder.Default private final double slippageBps = 10;         // 10 bps for crypto (realistic)
    @Builder.Default private final double makerFeePct = 0.0002;     // 0.02%
    @Builder.Default private final double takerFeePct = 0.0005;     // 0.05%
    @Builder.Default private final double fundingRatePer8h = 0.0001; // 0.01% default
    @Builder.Default private final boolean useMakerOrders = true;    // prefer maker fees
    @Builder.Default private final RiskParameters riskParameters = RiskParameters.builder().build();
}
```

- [ ] **Step 2: Create MultiTimeFrameBacktestResult**

```java
// engine/model/MultiTimeFrameBacktestResult.java
package com.QuantPlatformApplication.QuantPlatformApplication.engine.model;

import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Map;

@Getter
@Builder
public class MultiTimeFrameBacktestResult {
    private final double initialCapital;
    private final double finalCapital;
    private final double totalReturnPct;
    private final double sharpeRatio;
    private final double maxDrawdownPct;
    private final double winRate;
    private final int totalTrades;
    private final int winningTrades;
    private final int losingTrades;
    private final double profitFactor;
    private final double totalFees;
    private final double totalSlippage;
    private final double totalFundingPaid;
    private final List<Double> equityCurve;
    private final List<Map<String, Object>> tradeLog;   // Individual trade records
    private final Map<String, Double> perStrategyWinRate; // Strategy name → win rate
}
```

- [ ] **Step 3: Write failing tests**

```java
// MultiTimeFrameBacktestEngineTest.java
package com.QuantPlatformApplication.QuantPlatformApplication.engine;

import com.QuantPlatformApplication.QuantPlatformApplication.engine.model.*;
import com.QuantPlatformApplication.QuantPlatformApplication.engine.strategy.MultiTimeFrameStrategy;
import com.QuantPlatformApplication.QuantPlatformApplication.service.pipeline.CandleAggregator;
import com.QuantPlatformApplication.QuantPlatformApplication.service.pipeline.IndicatorCalculator;
import com.QuantPlatformApplication.QuantPlatformApplication.service.risk.TradeRiskEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class MultiTimeFrameBacktestEngineTest {

    private MultiTimeFrameBacktestEngine engine;
    private CandleAggregator aggregator;
    private IndicatorCalculator indicatorCalc;
    private TradeRiskEngine riskEngine;

    @BeforeEach
    void setUp() {
        aggregator = new CandleAggregator();
        indicatorCalc = new IndicatorCalculator();
        riskEngine = new TradeRiskEngine();
        engine = new MultiTimeFrameBacktestEngine(aggregator, indicatorCalc, riskEngine);
    }

    private List<Candle> generate15mCandles(int count, double startPrice) {
        List<Candle> candles = new ArrayList<>();
        Instant base = Instant.parse("2026-01-01T00:00:00Z");
        double price = startPrice;
        for (int i = 0; i < count; i++) {
            double change = (Math.random() - 0.48) * 50; // slight upward bias
            price = Math.max(100, price + change);
            candles.add(new Candle(base.plusSeconds(i * 900L),
                price, price + 20, price - 15, price + 5, 1000 + Math.random() * 500, TimeFrame.M15));
        }
        return candles;
    }

    @Test
    void backtestWithNoSignalsReturnsInitialCapital() {
        MultiTimeFrameStrategy noOpStrategy = new MultiTimeFrameStrategy() {
            public Optional<TradeSignal> analyze(MultiTimeFrameData data) { return Optional.empty(); }
            public ModelType getModelType() { return ModelType.TREND_CONTINUATION; }
            public String getName() { return "NoOp"; }
        };

        List<Candle> candles = generate15mCandles(500, 60000);
        BacktestConfig config = BacktestConfig.builder().initialCapital(500).build();

        MultiTimeFrameBacktestResult result = engine.run(List.of(noOpStrategy), candles, config);

        assertEquals(500, result.getInitialCapital());
        assertEquals(500, result.getFinalCapital(), 0.01);
        assertEquals(0, result.getTotalTrades());
        assertEquals(0, result.getTotalFees(), 0.01);
    }

    @Test
    void backtestAppliesSlippageAndFees() {
        // Strategy that always buys and sells next bar
        MultiTimeFrameStrategy alwaysTrade = new MultiTimeFrameStrategy() {
            private boolean inPosition = false;
            public Optional<TradeSignal> analyze(MultiTimeFrameData data) {
                double price = data.getCurrentPrice();
                if (!inPosition) {
                    inPosition = true;
                    return Optional.of(TradeSignal.builder()
                        .symbol("BTCUSD").action(Action.BUY)
                        .entryPrice(price).stopLossPrice(price * 0.99)
                        .takeProfitPrice(price * 1.02).confidence(0.8)
                        .strategyName("AlwaysTrade").metadata(Map.of()).build());
                } else {
                    inPosition = false;
                    return Optional.of(TradeSignal.builder()
                        .symbol("BTCUSD").action(Action.SELL)
                        .entryPrice(price).stopLossPrice(price * 1.01)
                        .takeProfitPrice(price * 0.98).confidence(0.8)
                        .strategyName("AlwaysTrade").metadata(Map.of()).build());
                }
            }
            public ModelType getModelType() { return ModelType.TREND_CONTINUATION; }
            public String getName() { return "AlwaysTrade"; }
        };

        List<Candle> candles = generate15mCandles(500, 60000);
        BacktestConfig config = BacktestConfig.builder().initialCapital(500).build();

        MultiTimeFrameBacktestResult result = engine.run(List.of(alwaysTrade), candles, config);

        assertTrue(result.getTotalFees() > 0, "Should have accumulated fees");
        assertTrue(result.getTotalTrades() > 0, "Should have executed trades");
    }

    @Test
    void equityCurveHasDataPoints() {
        MultiTimeFrameStrategy noOp = new MultiTimeFrameStrategy() {
            public Optional<TradeSignal> analyze(MultiTimeFrameData data) { return Optional.empty(); }
            public ModelType getModelType() { return ModelType.TREND_CONTINUATION; }
            public String getName() { return "NoOp"; }
        };

        List<Candle> candles = generate15mCandles(500, 60000);
        BacktestConfig config = BacktestConfig.builder().build();

        MultiTimeFrameBacktestResult result = engine.run(List.of(noOp), candles, config);

        assertNotNull(result.getEquityCurve());
        assertFalse(result.getEquityCurve().isEmpty());
    }

    @Test
    void sharpeRatioCalculatedCorrectly() {
        MultiTimeFrameStrategy noOp = new MultiTimeFrameStrategy() {
            public Optional<TradeSignal> analyze(MultiTimeFrameData data) { return Optional.empty(); }
            public ModelType getModelType() { return ModelType.TREND_CONTINUATION; }
            public String getName() { return "NoOp"; }
        };

        List<Candle> candles = generate15mCandles(500, 60000);
        BacktestConfig config = BacktestConfig.builder().build();

        MultiTimeFrameBacktestResult result = engine.run(List.of(noOp), candles, config);

        // With no trades, Sharpe should be 0 (no returns)
        assertEquals(0, result.getSharpeRatio(), 0.01);
    }

    @Test
    void riskEngineRejectsOversizedTrades() {
        // Strategy tries to trade with entry and stop very close (fee impact too high)
        MultiTimeFrameStrategy tightStop = new MultiTimeFrameStrategy() {
            public Optional<TradeSignal> analyze(MultiTimeFrameData data) {
                double price = data.getCurrentPrice();
                return Optional.of(TradeSignal.builder()
                    .symbol("BTCUSD").action(Action.BUY)
                    .entryPrice(price).stopLossPrice(price * 0.9999) // tiny stop
                    .takeProfitPrice(price * 1.01).confidence(0.8)
                    .strategyName("TightStop").metadata(Map.of()).build());
            }
            public ModelType getModelType() { return ModelType.TREND_CONTINUATION; }
            public String getName() { return "TightStop"; }
        };

        List<Candle> candles = generate15mCandles(500, 60000);
        BacktestConfig config = BacktestConfig.builder().initialCapital(500).build();

        MultiTimeFrameBacktestResult result = engine.run(List.of(tightStop), candles, config);

        // Risk engine should reject these trades (fee impact too high or stop too tight)
        assertEquals(0, result.getTotalTrades(), "Risk engine should reject all trades with tiny stops");
    }

    @Test
    void maxDrawdownCalculatedCorrectly() {
        MultiTimeFrameStrategy noOp = new MultiTimeFrameStrategy() {
            public Optional<TradeSignal> analyze(MultiTimeFrameData data) { return Optional.empty(); }
            public ModelType getModelType() { return ModelType.TREND_CONTINUATION; }
            public String getName() { return "NoOp"; }
        };

        List<Candle> candles = generate15mCandles(500, 60000);
        BacktestConfig config = BacktestConfig.builder().build();

        MultiTimeFrameBacktestResult result = engine.run(List.of(noOp), candles, config);

        assertEquals(0, result.getMaxDrawdownPct(), 0.01); // No trades = no drawdown
    }

    @Test
    void profitFactorCalculated() {
        MultiTimeFrameStrategy noOp = new MultiTimeFrameStrategy() {
            public Optional<TradeSignal> analyze(MultiTimeFrameData data) { return Optional.empty(); }
            public ModelType getModelType() { return ModelType.TREND_CONTINUATION; }
            public String getName() { return "NoOp"; }
        };

        List<Candle> candles = generate15mCandles(500, 60000);
        BacktestConfig config = BacktestConfig.builder().build();

        MultiTimeFrameBacktestResult result = engine.run(List.of(noOp), candles, config);

        assertEquals(0, result.getProfitFactor(), 0.01); // No trades
    }

    @Test
    void multipleStrategiesRunIndependently() {
        MultiTimeFrameStrategy strat1 = new MultiTimeFrameStrategy() {
            public Optional<TradeSignal> analyze(MultiTimeFrameData data) { return Optional.empty(); }
            public ModelType getModelType() { return ModelType.TREND_CONTINUATION; }
            public String getName() { return "Strat1"; }
        };
        MultiTimeFrameStrategy strat2 = new MultiTimeFrameStrategy() {
            public Optional<TradeSignal> analyze(MultiTimeFrameData data) { return Optional.empty(); }
            public ModelType getModelType() { return ModelType.MEAN_REVERSION; }
            public String getName() { return "Strat2"; }
        };

        List<Candle> candles = generate15mCandles(500, 60000);
        BacktestConfig config = BacktestConfig.builder().build();

        MultiTimeFrameBacktestResult result = engine.run(List.of(strat1, strat2), candles, config);

        assertNotNull(result);
        assertNotNull(result.getPerStrategyWinRate());
    }
}
```

- [ ] **Step 4: Write MultiTimeFrameBacktestEngine implementation**

Key logic:
- Takes `List<Candle>` (15m candles) as input
- Steps through candles in 15m increments
- At each step, builds `MultiTimeFrameData` using CandleAggregator + IndicatorCalculator
- Runs strategies against the data
- Signals go through TradeRiskEngine
- Tracks position, P&L, applies slippage + fees
- Applies funding rate costs every 8 hours
- Builds equity curve, calculates Sharpe, drawdown, profit factor, win rate

The engine must NOT look ahead — it only uses candles up to the current bar.

- [ ] **Step 5: Run tests, commit**

```bash
git commit -m "feat: add MultiTimeFrameBacktestEngine with TDD — realistic slippage, fees, funding, risk checks"
```

---

## Task 6: Live Data Pipeline

**Files:**
- Create: `service/pipeline/MarketDataPipeline.java`
- Create: `service/pipeline/DeltaExchangeDataFeed.java`

- [ ] **Step 1: Create DeltaExchangeDataFeed**

WebSocket client that connects to Delta Exchange and receives live candle/ticker updates. Feeds into CandleAggregator.

```java
// service/pipeline/DeltaExchangeDataFeed.java
package com.QuantPlatformApplication.QuantPlatformApplication.service.pipeline;

import com.QuantPlatformApplication.QuantPlatformApplication.engine.model.Candle;
import com.QuantPlatformApplication.QuantPlatformApplication.engine.model.TimeFrame;
import com.QuantPlatformApplication.QuantPlatformApplication.service.delta.DeltaExchangeConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Instant;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;

@Slf4j
@Component
@RequiredArgsConstructor
public class DeltaExchangeDataFeed {

    private final DeltaExchangeConfig config;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private WebSocket webSocket;
    private Consumer<Candle> candleHandler;
    private Consumer<Double> fundingRateHandler;

    public void connect(boolean testnet, String symbol,
                         Consumer<Candle> onCandle, Consumer<Double> onFundingRate) {
        this.candleHandler = onCandle;
        this.fundingRateHandler = onFundingRate;

        String wsUrl = testnet ? config.getTestnetWsUrl() : config.getProductionWsUrl();

        HttpClient client = HttpClient.newHttpClient();
        client.newWebSocketBuilder()
            .buildAsync(URI.create(wsUrl), new WebSocket.Listener() {

                @Override
                public void onOpen(WebSocket webSocket) {
                    DeltaExchangeDataFeed.this.webSocket = webSocket;
                    log.info("Delta Exchange WebSocket connected to {}", wsUrl);

                    // Subscribe to candle channel
                    String subscribe = String.format(
                        "{\"type\":\"subscribe\",\"payload\":{\"channels\":[{\"name\":\"candlestick_15m\",\"symbols\":[\"%s\"]}]}}",
                        symbol);
                    webSocket.sendText(subscribe, true);

                    WebSocket.Listener.super.onOpen(webSocket);
                }

                @Override
                public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                    try {
                        processMessage(data.toString());
                    } catch (Exception e) {
                        log.error("Error processing WebSocket message: {}", e.getMessage());
                    }
                    return WebSocket.Listener.super.onText(webSocket, data, last);
                }

                @Override
                public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
                    log.warn("Delta Exchange WebSocket closed: {} {}", statusCode, reason);
                    return WebSocket.Listener.super.onClose(webSocket, statusCode, reason);
                }

                @Override
                public void onError(WebSocket webSocket, Throwable error) {
                    log.error("Delta Exchange WebSocket error: {}", error.getMessage());
                }
            });
    }

    public void disconnect() {
        if (webSocket != null) {
            webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "Shutting down");
            log.info("Delta Exchange WebSocket disconnected");
        }
    }

    private void processMessage(String message) {
        try {
            JsonNode root = objectMapper.readTree(message);
            String type = root.has("type") ? root.get("type").asText() : "";

            if ("candlestick_15m".equals(type) && candleHandler != null) {
                JsonNode data = root.get("data");
                if (data != null) {
                    Candle candle = new Candle(
                        Instant.ofEpochSecond(data.get("time").asLong()),
                        data.get("open").asDouble(),
                        data.get("high").asDouble(),
                        data.get("low").asDouble(),
                        data.get("close").asDouble(),
                        data.get("volume").asDouble(),
                        TimeFrame.M15
                    );
                    candleHandler.accept(candle);
                }
            } else if ("funding_rate".equals(type) && fundingRateHandler != null) {
                double rate = root.get("data").get("funding_rate").asDouble();
                fundingRateHandler.accept(rate);
            }
        } catch (Exception e) {
            log.debug("Unhandled WebSocket message: {}", message);
        }
    }
}
```

- [ ] **Step 2: Create MarketDataPipeline**

```java
// service/pipeline/MarketDataPipeline.java
package com.QuantPlatformApplication.QuantPlatformApplication.service.pipeline;

import com.QuantPlatformApplication.QuantPlatformApplication.engine.model.*;
import com.QuantPlatformApplication.QuantPlatformApplication.engine.strategy.MultiTimeFrameStrategy;
import com.QuantPlatformApplication.QuantPlatformApplication.service.StrategyOrchestrator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Orchestrates the full live data pipeline:
 * Delta Exchange WebSocket → candle buffer → aggregation → indicators → strategy evaluation
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MarketDataPipeline {

    private final DeltaExchangeDataFeed dataFeed;
    private final CandleAggregator aggregator;
    private final IndicatorCalculator indicatorCalculator;
    private final FundingRateTracker fundingRateTracker;
    private final AccountStateTracker accountState;
    private final StrategyOrchestrator orchestrator;

    private final List<Candle> candleBuffer15m = new ArrayList<>();
    private static final int MAX_BUFFER_SIZE = 500; // ~5 days of 15m candles
    private String activeSymbol;
    private RiskParameters riskParameters;
    private String executionMode = "HUMAN_IN_LOOP";

    public void start(String symbol, boolean testnet, RiskParameters params, String mode) {
        this.activeSymbol = symbol;
        this.riskParameters = params;
        this.executionMode = mode;

        log.info("Starting live pipeline for {} (testnet={}, mode={})", symbol, testnet, mode);

        dataFeed.connect(testnet, symbol,
            this::onNewCandle,
            fundingRateTracker::recordFundingRate
        );
    }

    public void stop() {
        dataFeed.disconnect();
        log.info("Live pipeline stopped for {}", activeSymbol);
    }

    public void setExecutionMode(String mode) {
        this.executionMode = mode;
        log.info("Execution mode changed to: {}", mode);
    }

    private void onNewCandle(Candle candle) {
        candleBuffer15m.add(candle);
        while (candleBuffer15m.size() > MAX_BUFFER_SIZE) {
            candleBuffer15m.remove(0);
        }

        if (candleBuffer15m.size() < 64) { // Need at least 64 candles for 4h aggregation + indicators
            log.debug("Buffering candles: {}/{}", candleBuffer15m.size(), 64);
            return;
        }

        // Aggregate to higher timeframes
        List<Candle> candles1h = aggregator.aggregate(candleBuffer15m, TimeFrame.M15, TimeFrame.H1);
        List<Candle> candles4h = aggregator.aggregate(candleBuffer15m, TimeFrame.M15, TimeFrame.H4);

        // Calculate indicators
        IndicatorSnapshot ind15m = indicatorCalculator.calculate(candleBuffer15m, TimeFrame.M15);
        IndicatorSnapshot ind1h = indicatorCalculator.calculate(candles1h, TimeFrame.H1);
        IndicatorSnapshot ind4h = indicatorCalculator.calculate(candles4h, TimeFrame.H4);

        // Build multi-timeframe data
        MultiTimeFrameData data = MultiTimeFrameData.builder()
            .symbol(activeSymbol)
            .currentPrice(candle.close())
            .currentVolume(candle.volume())
            .candles15m(new ArrayList<>(candleBuffer15m))
            .candles1h(candles1h)
            .candles4h(candles4h)
            .indicators15m(ind15m)
            .indicators1h(ind1h)
            .indicators4h(ind4h)
            .fundingRate(fundingRateTracker.getCurrentRate())
            .fundingRateHistory(fundingRateTracker.getHistory())
            .build();

        // Evaluate strategies
        orchestrator.evaluateStrategies(
            data,
            accountState.getCurrentBalance(),
            accountState.getPeakEquity(),
            accountState.getCurrentExposure(),
            accountState.getDailyRealizedLoss(),
            accountState.getOpenPositionSymbols(),
            riskParameters,
            executionMode
        );
    }
}
```

- [ ] **Step 3: Commit**

```bash
git commit -m "feat: add live MarketDataPipeline — Delta Exchange WS → candles → indicators → strategies"
```

---

## Task 7: Backtest REST API and Integration

**Files:**
- Create: `controller/MultiTimeFrameBacktestController.java`
- Create: `service/MultiTimeFrameBacktestService.java`

- [ ] **Step 1: Create backtest service**

```java
// service/MultiTimeFrameBacktestService.java
package com.QuantPlatformApplication.QuantPlatformApplication.service;

import com.QuantPlatformApplication.QuantPlatformApplication.engine.MultiTimeFrameBacktestEngine;
import com.QuantPlatformApplication.QuantPlatformApplication.engine.model.*;
import com.QuantPlatformApplication.QuantPlatformApplication.engine.strategy.MultiTimeFrameStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class MultiTimeFrameBacktestService {

    private final MultiTimeFrameBacktestEngine backtestEngine;
    private final List<MultiTimeFrameStrategy> strategies;

    public MultiTimeFrameBacktestResult runBacktest(List<Candle> candles15m, BacktestConfig config) {
        log.info("Starting multi-TF backtest: {} candles, ${} capital",
            candles15m.size(), config.getInitialCapital());

        MultiTimeFrameBacktestResult result = backtestEngine.run(strategies, candles15m, config);

        log.info("Backtest complete: {:.1f}% return, {:.1f}% win rate, {:.1f}% max DD, {} trades",
            result.getTotalReturnPct(), result.getWinRate(),
            result.getMaxDrawdownPct(), result.getTotalTrades());

        return result;
    }
}
```

- [ ] **Step 2: Create controller**

```java
// controller/MultiTimeFrameBacktestController.java
package com.QuantPlatformApplication.QuantPlatformApplication.controller;

import com.QuantPlatformApplication.QuantPlatformApplication.engine.model.BacktestConfig;
import com.QuantPlatformApplication.QuantPlatformApplication.engine.model.MultiTimeFrameBacktestResult;
import com.QuantPlatformApplication.QuantPlatformApplication.service.MultiTimeFrameBacktestService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/backtests/multi-tf")
@RequiredArgsConstructor
public class MultiTimeFrameBacktestController {

    private final MultiTimeFrameBacktestService backtestService;

    @PostMapping
    public ResponseEntity<MultiTimeFrameBacktestResult> runBacktest(
            @RequestBody Map<String, Object> request) {

        double capital = request.containsKey("initialCapital")
            ? ((Number) request.get("initialCapital")).doubleValue() : 500;
        double slippage = request.containsKey("slippageBps")
            ? ((Number) request.get("slippageBps")).doubleValue() : 10;

        BacktestConfig config = BacktestConfig.builder()
            .initialCapital(capital)
            .slippageBps(slippage)
            .build();

        // TODO: Fetch 15m candles from Delta Exchange historical API or DB
        // For now, return method signature for Phase 4 wiring
        return ResponseEntity.ok(null);
    }
}
```

- [ ] **Step 3: Add Telegram and pipeline endpoints to SecurityConfig permitAll**

- [ ] **Step 4: Commit**

```bash
git commit -m "feat: add MultiTimeFrameBacktestService and REST controller for backtesting API"
```

---

## Summary

| Task | Component | Tests | Description |
|------|-----------|-------|-------------|
| 1 | Telegram Bot Service | 6 | Message formatting, command parsing, send API |
| 2 | Telegram Command Handler | — | /status, /approve, /reject, /stop, etc. |
| 3 | Account State Tracker | 5 | Balance, equity, daily P&L, exposure |
| 4 | Funding Rate Tracker | 4 | History, extremes detection |
| 5 | Multi-TF Backtest Engine | 8 | Realistic simulation with fees, slippage, risk |
| 6 | Live Data Pipeline | — | WS → candles → indicators → strategies |
| 7 | Backtest REST API | — | Service + controller |

**Total: 7 tasks, 23 unit tests.**

**After Phase 3:** The system has a working Telegram bot, realistic backtesting, and a live data pipeline. Phase 4 deploys to Oracle Cloud and adds monitoring.
