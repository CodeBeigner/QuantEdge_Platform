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
