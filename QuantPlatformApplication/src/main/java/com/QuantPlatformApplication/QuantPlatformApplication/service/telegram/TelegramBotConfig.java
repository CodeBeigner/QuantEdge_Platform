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
