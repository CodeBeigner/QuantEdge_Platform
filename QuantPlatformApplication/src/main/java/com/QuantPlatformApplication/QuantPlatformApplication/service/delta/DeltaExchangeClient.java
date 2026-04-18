package com.QuantPlatformApplication.QuantPlatformApplication.service.delta;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Map;

@Slf4j
@Component
public class DeltaExchangeClient {

    private final DeltaExchangeConfig config;
    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public DeltaExchangeClient(DeltaExchangeConfig config) {
        this.config = config;
        this.objectMapper = new ObjectMapper();
        this.webClient = WebClient.builder()
            .codecs(c -> c.defaultCodecs().maxInMemorySize(2 * 1024 * 1024))
            .build();
    }

    public String getBaseUrl(boolean testnet) {
        return testnet ? config.getTestnetBaseUrl() : config.getProductionBaseUrl();
    }

    public String generateSignature(String method, String timestamp, String path,
                                     String queryString, String body, String apiSecret) {
        try {
            String payload = method + timestamp + path + queryString + body;
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(apiSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate HMAC signature", e);
        }
    }

    // --- Public API (no auth) ---

    public Mono<JsonNode> getProducts(boolean testnet) {
        String baseUrl = getBaseUrl(testnet);
        return webClient.get()
            .uri(baseUrl + "/v2/products")
            .retrieve()
            .bodyToMono(JsonNode.class)
            .timeout(Duration.ofSeconds(config.getTimeoutSeconds()));
    }

    public Mono<JsonNode> getTicker(String symbol, boolean testnet) {
        String baseUrl = getBaseUrl(testnet);
        return webClient.get()
            .uri(baseUrl + "/v2/tickers/" + symbol)
            .retrieve()
            .bodyToMono(JsonNode.class)
            .timeout(Duration.ofSeconds(config.getTimeoutSeconds()));
    }

    public Mono<JsonNode> getOrderBook(int productId, int depth, boolean testnet) {
        String baseUrl = getBaseUrl(testnet);
        return webClient.get()
            .uri(baseUrl + "/v2/l2orderbook/" + productId + "?depth=" + depth)
            .retrieve()
            .bodyToMono(JsonNode.class)
            .timeout(Duration.ofSeconds(config.getTimeoutSeconds()));
    }

    // --- Authenticated API ---

    public Mono<JsonNode> authenticatedGet(String path, String apiKey, String apiSecret, boolean testnet) {
        String baseUrl = getBaseUrl(testnet);
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
        String signature = generateSignature("GET", timestamp, path, "", "", apiSecret);

        return webClient.get()
            .uri(baseUrl + path)
            .header("api-key", apiKey)
            .header("timestamp", timestamp)
            .header("signature", signature)
            .header("Content-Type", "application/json")
            .retrieve()
            .bodyToMono(JsonNode.class)
            .timeout(Duration.ofSeconds(config.getTimeoutSeconds()));
    }

    public Mono<JsonNode> authenticatedPost(String path, String body,
                                             String apiKey, String apiSecret, boolean testnet) {
        String baseUrl = getBaseUrl(testnet);
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
        String signature = generateSignature("POST", timestamp, path, "", body, apiSecret);

        return webClient.post()
            .uri(baseUrl + path)
            .header("api-key", apiKey)
            .header("timestamp", timestamp)
            .header("signature", signature)
            .header("Content-Type", "application/json")
            .bodyValue(body)
            .retrieve()
            .bodyToMono(JsonNode.class)
            .timeout(Duration.ofSeconds(config.getTimeoutSeconds()));
    }

    public Mono<JsonNode> authenticatedDelete(String path, String apiKey, String apiSecret, boolean testnet) {
        String baseUrl = getBaseUrl(testnet);
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
        String signature = generateSignature("DELETE", timestamp, path, "", "", apiSecret);

        return webClient.delete()
            .uri(baseUrl + path)
            .header("api-key", apiKey)
            .header("timestamp", timestamp)
            .header("signature", signature)
            .header("Content-Type", "application/json")
            .retrieve()
            .bodyToMono(JsonNode.class)
            .timeout(Duration.ofSeconds(config.getTimeoutSeconds()));
    }

    // --- Order Payload Builders ---

    public String buildOrderPayload(int productId, int size, String side,
                                     String orderType, String limitPrice) {
        try {
            ObjectNode node = objectMapper.createObjectNode();
            node.put("product_id", productId);
            node.put("size", size);
            node.put("side", side);
            node.put("order_type", orderType);
            if (limitPrice != null && !"market_order".equals(orderType)) {
                node.put("limit_price", limitPrice);
            }
            return objectMapper.writeValueAsString(node);
        } catch (Exception e) {
            throw new RuntimeException("Failed to build order payload", e);
        }
    }

    public String buildBracketOrderPayload(int productId, int size, String side,
                                            String orderType, String limitPrice,
                                            String stopLossPrice, String takeProfitPrice) {
        try {
            ObjectNode node = objectMapper.createObjectNode();
            node.put("product_id", productId);
            node.put("size", size);
            node.put("side", side);
            node.put("order_type", orderType);
            if (limitPrice != null) {
                node.put("limit_price", limitPrice);
            }
            if (stopLossPrice != null) {
                node.put("stop_loss_price", stopLossPrice);
            }
            if (takeProfitPrice != null) {
                node.put("take_profit_price", takeProfitPrice);
            }
            return objectMapper.writeValueAsString(node);
        } catch (Exception e) {
            throw new RuntimeException("Failed to build bracket order payload", e);
        }
    }

    // --- High-Level Methods ---

    public Mono<JsonNode> getBalances(String apiKey, String apiSecret, boolean testnet) {
        return authenticatedGet("/v2/wallet/balances", apiKey, apiSecret, testnet);
    }

    public Mono<JsonNode> getPositions(String apiKey, String apiSecret, boolean testnet) {
        return authenticatedGet("/v2/positions/margined", apiKey, apiSecret, testnet);
    }

    public Mono<JsonNode> getOpenOrders(String apiKey, String apiSecret, boolean testnet) {
        return authenticatedGet("/v2/orders", apiKey, apiSecret, testnet);
    }

    public Mono<JsonNode> placeOrder(String orderPayload, String apiKey, String apiSecret, boolean testnet) {
        return authenticatedPost("/v2/orders", orderPayload, apiKey, apiSecret, testnet);
    }

    public Mono<JsonNode> placeBracketOrder(String orderPayload, String apiKey, String apiSecret, boolean testnet) {
        return authenticatedPost("/v2/orders/bracket", orderPayload, apiKey, apiSecret, testnet);
    }

    public Mono<JsonNode> cancelOrder(int orderId, int productId, String apiKey, String apiSecret, boolean testnet) {
        return authenticatedDelete("/v2/orders/" + orderId + "?product_id=" + productId, apiKey, apiSecret, testnet);
    }

    public Mono<JsonNode> setLeverage(int productId, int leverage, String apiKey, String apiSecret, boolean testnet) {
        try {
            String body = objectMapper.writeValueAsString(Map.of("leverage", leverage));
            return authenticatedPost("/v2/products/" + productId + "/orders/leverage", body, apiKey, apiSecret, testnet);
        } catch (Exception e) {
            return Mono.error(new RuntimeException("Failed to build leverage payload", e));
        }
    }
}
