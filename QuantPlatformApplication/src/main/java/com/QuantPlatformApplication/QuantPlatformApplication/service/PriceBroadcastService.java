package com.QuantPlatformApplication.QuantPlatformApplication.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Broadcasts market data updates to WebSocket subscribers.
 * Runs every 5 seconds, pushing the latest data to /topic/prices.
 */
@Slf4j
@Service
public class PriceBroadcastService {

    private final SimpMessagingTemplate messagingTemplate;
    private final MarketDataService marketDataService;

    public PriceBroadcastService(SimpMessagingTemplate messagingTemplate,
            MarketDataService marketDataService) {
        this.messagingTemplate = messagingTemplate;
        this.marketDataService = marketDataService;
    }

    @Scheduled(fixedRate = 5000)
    public void broadcastPrices() {
        try {
            var symbols = marketDataService.getAvailableSymbols();
            for (String symbol : symbols) {
                var recent = marketDataService.fetchRecentData(symbol, 1);
                if (!recent.isEmpty()) {
                    var latest = recent.getLast();
                    messagingTemplate.convertAndSend("/topic/prices/" + symbol, Map.of(
                            "symbol", symbol,
                            "price", latest.getClose(),
                            "volume", latest.getVolume(),
                            "time", latest.getTime().toString()));
                }
            }
        } catch (Exception e) {
            log.debug("Price broadcast skipped: {}", e.getMessage());
        }
    }
}
