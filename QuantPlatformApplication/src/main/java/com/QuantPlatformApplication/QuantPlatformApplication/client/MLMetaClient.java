package com.QuantPlatformApplication.QuantPlatformApplication.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * Typed client for ml-service's /predict-meta endpoint.
 *
 * Intentionally thin: this commit just wraps the HTTP call and DTO. Integration
 * into TradeRiskEngine as a veto is scoped to Plan 4 (paper trading wire-up) so
 * that changing the gating semantics is its own reviewable change.
 */
@Slf4j
@Component
public class MLMetaClient {

    private final String baseUrl;
    private final ObjectMapper objectMapper;
    private final HttpClient http;

    public MLMetaClient(
            @Value("${quantedge.ml.url:http://localhost:5001}") String baseUrl,
            ObjectMapper objectMapper) {
        this.baseUrl = baseUrl;
        this.objectMapper = objectMapper;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    public MLMetaPredictionResponse predictMeta(
            String symbol, String primarySignal,
            double entryPrice, double tpPct, double slPct) {
        try {
            String body = objectMapper.writeValueAsString(Map.of(
                    "primary_signal", primarySignal,
                    "entry_price", entryPrice,
                    "tp_pct", tpPct,
                    "sl_pct", slPct));

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/predict-meta/" + symbol))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() >= 300) {
                throw new RuntimeException(
                        "predict-meta returned " + resp.statusCode() + ": " + resp.body());
            }
            return objectMapper.readValue(resp.body(), MLMetaPredictionResponse.class);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("predict-meta call failed", e);
        }
    }
}
