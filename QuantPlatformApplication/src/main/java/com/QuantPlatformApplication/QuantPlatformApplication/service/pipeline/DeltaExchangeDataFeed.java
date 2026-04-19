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
