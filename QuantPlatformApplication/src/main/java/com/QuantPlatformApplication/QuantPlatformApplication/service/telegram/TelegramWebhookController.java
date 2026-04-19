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
