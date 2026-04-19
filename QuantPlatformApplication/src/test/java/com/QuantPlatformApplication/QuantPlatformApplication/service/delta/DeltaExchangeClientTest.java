package com.QuantPlatformApplication.QuantPlatformApplication.service.delta;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DeltaExchangeClientTest {

    private DeltaExchangeClient client;

    @BeforeEach
    void setUp() {
        DeltaExchangeConfig config = new DeltaExchangeConfig();
        client = new DeltaExchangeClient(config);
    }

    @Test
    void generatesCorrectHmacSignature() {
        // Delta Exchange uses HMAC-SHA256: method + timestamp + path + query + body
        String signature = client.generateSignature(
            "GET", "1234567890", "/v2/orders", "", "", "test-secret"
        );
        assertNotNull(signature);
        assertEquals(64, signature.length()); // SHA256 hex is 64 chars
    }

    @Test
    void generatesCorrectSignatureForPost() {
        String signature = client.generateSignature(
            "POST", "1234567890", "/v2/orders", "",
            "{\"product_id\":1,\"size\":1}", "test-secret"
        );
        assertNotNull(signature);
        assertEquals(64, signature.length());
    }

    @Test
    void differentInputsProduceDifferentSignatures() {
        String sig1 = client.generateSignature(
            "GET", "1234567890", "/v2/orders", "", "", "secret-1"
        );
        String sig2 = client.generateSignature(
            "GET", "1234567890", "/v2/orders", "", "", "secret-2"
        );
        assertNotEquals(sig1, sig2);
    }

    @Test
    void baseUrlIsTestnetByDefault() {
        String url = client.getBaseUrl(true);
        assertTrue(url.contains("testnet"));
    }

    @Test
    void baseUrlIsProductionWhenSpecified() {
        String url = client.getBaseUrl(false);
        assertFalse(url.contains("testnet"));
        assertTrue(url.contains("api.india.delta.exchange"));
    }

    @Test
    void buildOrderPayloadHasRequiredFields() {
        String payload = client.buildOrderPayload(1, 10, "buy", "limit_order", "67000");

        assertTrue(payload.contains("\"product_id\":1"));
        assertTrue(payload.contains("\"size\":10"));
        assertTrue(payload.contains("\"side\":\"buy\""));
        assertTrue(payload.contains("\"order_type\":\"limit_order\""));
        assertTrue(payload.contains("\"limit_price\":\"67000\""));
    }

    @Test
    void buildMarketOrderPayloadOmitsLimitPrice() {
        String payload = client.buildOrderPayload(1, 5, "buy", "market_order", null);

        assertTrue(payload.contains("\"product_id\":1"));
        assertTrue(payload.contains("\"size\":5"));
        assertTrue(payload.contains("\"order_type\":\"market_order\""));
        assertFalse(payload.contains("limit_price"));
    }

    @Test
    void buildBracketOrderPayloadIncludesStopAndTakeProfit() {
        String payload = client.buildBracketOrderPayload(
            1, 10, "buy", "limit_order", "67000", "66700", "67900"
        );

        assertTrue(payload.contains("\"stop_loss_price\":\"66700\""));
        assertTrue(payload.contains("\"take_profit_price\":\"67900\""));
        assertTrue(payload.contains("\"product_id\":1"));
    }
}
