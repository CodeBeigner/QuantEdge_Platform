package com.QuantPlatformApplication.QuantPlatformApplication.service.delta;

import com.QuantPlatformApplication.QuantPlatformApplication.config.EncryptionConfig;
import com.QuantPlatformApplication.QuantPlatformApplication.model.entity.DeltaCredential;
import com.QuantPlatformApplication.QuantPlatformApplication.repository.DeltaCredentialRepository;
import com.QuantPlatformApplication.QuantPlatformApplication.service.broker.BrokerAdapter;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class DeltaExchangeBrokerAdapter implements BrokerAdapter {

    private final DeltaExchangeClient client;
    private final DeltaCredentialRepository credentialRepo;
    private final EncryptionConfig encryption;

    // Default to user ID 1 for single-user mode. Will be parameterized for multi-tenant.
    private long activeUserId = 1L;
    private boolean useTestnet = true;

    @Override
    public String getName() {
        return "DELTA_EXCHANGE";
    }

    @Override
    public boolean isConnected() {
        try {
            String[] keys = getDecryptedKeys();
            if (keys == null) return false;
            JsonNode result = client.getBalances(keys[0], keys[1], useTestnet).block();
            return result != null;
        } catch (Exception e) {
            log.warn("Delta Exchange connection check failed: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public Map<String, Object> placeOrder(String symbol, String side, String type,
                                           double quantity, Double price) {
        String[] keys = requireKeys();
        String orderType = "MARKET".equalsIgnoreCase(type) ? "market_order" : "limit_order";
        String limitPrice = price != null ? String.valueOf(price.intValue()) : null;

        int productId = resolveProductId(symbol);
        String payload = client.buildOrderPayload(productId, (int) quantity, side.toLowerCase(), orderType, limitPrice);

        JsonNode result = client.placeOrder(payload, keys[0], keys[1], useTestnet).block();
        return jsonToMap(result);
    }

    @Override
    public Map<String, Object> cancelOrder(String orderId) {
        String[] keys = requireKeys();
        // orderId format: "orderId:productId"
        String[] parts = orderId.split(":");
        int oid = Integer.parseInt(parts[0]);
        int pid = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;

        JsonNode result = client.cancelOrder(oid, pid, keys[0], keys[1], useTestnet).block();
        return jsonToMap(result);
    }

    @Override
    public Map<String, Object> getOrder(String orderId) {
        // Delta Exchange doesn't have a single-order GET. Return from order history.
        String[] keys = requireKeys();
        JsonNode result = client.getOpenOrders(keys[0], keys[1], useTestnet).block();
        return jsonToMap(result);
    }

    @Override
    public List<Map<String, Object>> getOpenOrders() {
        String[] keys = requireKeys();
        JsonNode result = client.getOpenOrders(keys[0], keys[1], useTestnet).block();
        return jsonToList(result);
    }

    @Override
    public List<Map<String, Object>> getPositions() {
        String[] keys = requireKeys();
        JsonNode result = client.getPositions(keys[0], keys[1], useTestnet).block();
        return jsonToList(result);
    }

    @Override
    public Map<String, Object> getAccount() {
        String[] keys = requireKeys();
        JsonNode result = client.getBalances(keys[0], keys[1], useTestnet).block();
        return jsonToMap(result);
    }

    @Override
    public Map<String, Object> reconcilePositions() {
        // Fetch positions from exchange and return them as source of truth
        String[] keys = requireKeys();
        JsonNode positions = client.getPositions(keys[0], keys[1], useTestnet).block();
        JsonNode balances = client.getBalances(keys[0], keys[1], useTestnet).block();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("positions", jsonToList(positions));
        result.put("balances", jsonToMap(balances));
        result.put("source", "DELTA_EXCHANGE");
        result.put("testnet", useTestnet);
        return result;
    }

    public void setActiveUser(long userId, boolean testnet) {
        this.activeUserId = userId;
        this.useTestnet = testnet;
    }

    private String[] getDecryptedKeys() {
        Optional<DeltaCredential> cred = credentialRepo.findByUserIdAndIsTestnet(activeUserId, useTestnet);
        if (cred.isEmpty()) return null;
        return new String[]{
            encryption.decrypt(cred.get().getApiKeyEncrypted()),
            encryption.decrypt(cred.get().getApiSecretEncrypted())
        };
    }

    private String[] requireKeys() {
        String[] keys = getDecryptedKeys();
        if (keys == null) {
            throw new IllegalStateException("No Delta Exchange credentials configured for user " + activeUserId);
        }
        return keys;
    }

    private int resolveProductId(String symbol) {
        // Common Delta Exchange India product IDs (can be cached from products API)
        return switch (symbol.toUpperCase()) {
            case "BTCUSD", "BTCUSDT" -> 84;
            case "ETHUSD", "ETHUSDT" -> 85;
            default -> {
                try {
                    yield Integer.parseInt(symbol);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Unknown symbol: " + symbol + ". Use product ID directly.");
                }
            }
        };
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> jsonToMap(JsonNode node) {
        if (node == null) return Map.of();
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.convertValue(node, LinkedHashMap.class);
        } catch (Exception e) {
            return Map.of("raw", node.toString());
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> jsonToList(JsonNode node) {
        if (node == null) return List.of();
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            if (node.has("result")) {
                node = node.get("result");
            }
            if (node.isArray()) {
                List<Map<String, Object>> result = new ArrayList<>();
                for (JsonNode item : node) {
                    result.add(mapper.convertValue(item, LinkedHashMap.class));
                }
                return result;
            }
            return List.of(mapper.convertValue(node, LinkedHashMap.class));
        } catch (Exception e) {
            return List.of(Map.of("raw", node.toString()));
        }
    }
}
