package com.QuantPlatformApplication.QuantPlatformApplication.service.broker;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Alpaca Markets broker adapter for paper and live trading.
 * Requires ALPACA_API_KEY and ALPACA_SECRET_KEY environment variables.
 */
@Slf4j
@Component
public class AlpacaBrokerAdapter implements BrokerAdapter {

    private final WebClient webClient;
    @SuppressWarnings("unused")
    private final boolean isConfigured;

    @Value("${alpaca.api-key:}")
    private String apiKey;

    @Value("${alpaca.secret-key:}")
    private String secretKey;

    @Value("${alpaca.base-url:https://paper-api.alpaca.markets}")
    private String baseUrl;

    public AlpacaBrokerAdapter() {
        this.webClient = WebClient.builder().build();
        this.isConfigured = false;
    }

    @Override
    public String getName() {
        return "ALPACA";
    }

    @Override
    public boolean isConnected() {
        if (apiKey == null || apiKey.isEmpty()) return false;
        try {
            var response = webClient.get()
                    .uri(baseUrl + "/v2/account")
                    .header("APCA-API-KEY-ID", apiKey)
                    .header("APCA-API-SECRET-KEY", secretKey)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();
            return response != null && response.containsKey("id");
        } catch (Exception e) {
            log.warn("Alpaca connection check failed: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public Map<String, Object> placeOrder(String symbol, String side, String type, double quantity, Double price) {
        if (!isConnected()) return Map.of("status", "REJECTED", "error", "Alpaca not configured");

        Map<String, Object> orderBody = new HashMap<>();
        orderBody.put("symbol", symbol);
        orderBody.put("qty", String.valueOf((int) quantity));
        orderBody.put("side", side.toLowerCase());
        orderBody.put("type", type.toLowerCase());
        orderBody.put("time_in_force", "day");
        if (price != null && !"market".equalsIgnoreCase(type)) {
            orderBody.put("limit_price", String.valueOf(price));
        }

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = webClient.post()
                    .uri(baseUrl + "/v2/orders")
                    .header("APCA-API-KEY-ID", apiKey)
                    .header("APCA-API-SECRET-KEY", secretKey)
                    .bodyValue(orderBody)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            return Map.of("status", "SUBMITTED", "broker", "ALPACA", "result", response != null ? response : Map.of());
        } catch (Exception e) {
            return Map.of("status", "REJECTED", "error", e.getMessage(), "broker", "ALPACA");
        }
    }

    @Override
    public Map<String, Object> cancelOrder(String orderId) {
        if (!isConnected()) return Map.of("status", "FAILED", "error", "Not configured");
        try {
            webClient.delete()
                    .uri(baseUrl + "/v2/orders/" + orderId)
                    .header("APCA-API-KEY-ID", apiKey)
                    .header("APCA-API-SECRET-KEY", secretKey)
                    .retrieve()
                    .bodyToMono(Void.class)
                    .block();
            return Map.of("status", "CANCELLED", "orderId", orderId);
        } catch (Exception e) {
            return Map.of("status", "FAILED", "error", e.getMessage());
        }
    }

    @Override
    public Map<String, Object> getOrder(String orderId) {
        if (!isConnected()) return Map.of("error", "Not configured");
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = webClient.get()
                    .uri(baseUrl + "/v2/orders/" + orderId)
                    .header("APCA-API-KEY-ID", apiKey)
                    .header("APCA-API-SECRET-KEY", secretKey)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();
            return response != null ? response : Map.of();
        } catch (Exception e) {
            return Map.of("error", e.getMessage());
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getOpenOrders() {
        if (!isConnected()) return List.of();
        try {
            List<Map<String, Object>> response = webClient.get()
                    .uri(baseUrl + "/v2/orders?status=open")
                    .header("APCA-API-KEY-ID", apiKey)
                    .header("APCA-API-SECRET-KEY", secretKey)
                    .retrieve()
                    .bodyToMono(List.class)
                    .block();
            return response != null ? response : List.of();
        } catch (Exception e) {
            return List.of();
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getPositions() {
        if (!isConnected()) return List.of();
        try {
            List<Map<String, Object>> response = webClient.get()
                    .uri(baseUrl + "/v2/positions")
                    .header("APCA-API-KEY-ID", apiKey)
                    .header("APCA-API-SECRET-KEY", secretKey)
                    .retrieve()
                    .bodyToMono(List.class)
                    .block();
            return response != null ? response : List.of();
        } catch (Exception e) {
            return List.of();
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> getAccount() {
        if (!isConnected()) return Map.of("error", "Not configured");
        try {
            Map<String, Object> response = webClient.get()
                    .uri(baseUrl + "/v2/account")
                    .header("APCA-API-KEY-ID", apiKey)
                    .header("APCA-API-SECRET-KEY", secretKey)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();
            return response != null ? response : Map.of();
        } catch (Exception e) {
            return Map.of("error", e.getMessage());
        }
    }

    @Override
    public Map<String, Object> reconcilePositions() {
        if (!isConnected()) return Map.of("status", "SKIPPED", "reason", "Not configured");
        List<Map<String, Object>> positions = getPositions();
        return Map.of(
                "status", "OK",
                "broker", "ALPACA",
                "positionCount", positions.size(),
                "positions", positions
        );
    }
}
