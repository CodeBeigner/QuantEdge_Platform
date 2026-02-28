package com.QuantPlatformApplication.QuantPlatformApplication.service;

import com.QuantPlatformApplication.QuantPlatformApplication.model.entity.MLSignal;
import com.QuantPlatformApplication.QuantPlatformApplication.repository.MLSignalRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * ML Client Service — Communicates with the Python ML microservice.
 *
 * Calls the FastAPI endpoints for training, prediction, feature
 * engineering, and portfolio optimization. Persists signal results.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MLClientService {

    private final MLSignalRepository signalRepo;
    private final RestTemplate restTemplate;

    private static final String ML_SERVICE_URL = "http://localhost:5001";

    /**
     * Get ML prediction for a symbol from the Python service.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> predict(String symbol) {
        try {
            ResponseEntity<Map> res = restTemplate.postForEntity(
                    ML_SERVICE_URL + "/predict/" + symbol, null, Map.class);
            Map<String, Object> result = res.getBody();

            if (result != null && result.containsKey("signal")) {
                MLSignal sig = new MLSignal();
                sig.setSymbol(symbol);
                sig.setSignal((String) result.get("signal"));
                Object conf = result.get("confidence");
                if (conf instanceof Number) {
                    sig.setConfidence(BigDecimal.valueOf(((Number) conf).doubleValue()));
                }
                Object acc = result.get("model_accuracy");
                if (acc instanceof Number) {
                    sig.setModelAccuracy(BigDecimal.valueOf(((Number) acc).doubleValue()));
                }
                signalRepo.save(sig);
            }
            return result;
        } catch (Exception e) {
            log.warn("ML service unavailable: {}", e.getMessage());
            return Map.of("error", "ML service unavailable", "message", e.getMessage());
        }
    }

    /**
     * Train ML model for a symbol.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> train(String symbol) {
        try {
            ResponseEntity<Map> res = restTemplate.postForEntity(
                    ML_SERVICE_URL + "/train/" + symbol, null, Map.class);
            return res.getBody();
        } catch (Exception e) {
            log.warn("ML training failed: {}", e.getMessage());
            return Map.of("error", "ML service unavailable");
        }
    }

    /**
     * Get technical features for a symbol.
     */
    @SuppressWarnings("unchecked")
    public Object getFeatures(String symbol) {
        try {
            return restTemplate.getForObject(ML_SERVICE_URL + "/features/" + symbol, List.class);
        } catch (Exception e) {
            return Map.of("error", "ML service unavailable");
        }
    }

    /**
     * Run portfolio optimization.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> optimize(List<String> symbols) {
        try {
            ResponseEntity<Map> res = restTemplate.postForEntity(
                    ML_SERVICE_URL + "/optimize", Map.of("symbols", symbols), Map.class);
            return res.getBody();
        } catch (Exception e) {
            return Map.of("error", "ML service unavailable");
        }
    }

    /**
     * Get recent ML signals from the database.
     */
    public List<MLSignal> getRecentSignals() {
        return signalRepo.findTop10ByOrderByCreatedAtDesc();
    }

    /**
     * Check ML service health.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> health() {
        try {
            return restTemplate.getForObject(ML_SERVICE_URL + "/health", Map.class);
        } catch (Exception e) {
            return Map.of("status", "DOWN", "error", e.getMessage());
        }
    }
}
