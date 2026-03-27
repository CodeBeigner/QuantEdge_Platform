package com.QuantPlatformApplication.QuantPlatformApplication.event;

import com.QuantPlatformApplication.QuantPlatformApplication.config.KafkaConfig;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Publishes domain events to Kafka topics.
 *
 * <p>All payloads are serialized to JSON before publishing.
 * Failures are logged but do not throw — fire-and-forget semantics.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "spring.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class EventPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public EventPublisher(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Publish raw market data event.
     *
     * @param symbol the market symbol
     * @param data   the market data payload
     */
    public void publishMarketData(String symbol, Map<String, Object> data) {
        publish(KafkaConfig.MARKET_DATA_TOPIC, symbol, data);
    }

    /**
     * Publish a strategy signal event.
     *
     * @param strategyId the strategy ID
     * @param signal     the signal payload
     */
    public void publishStrategySignal(String strategyId, Map<String, Object> signal) {
        publish(KafkaConfig.STRATEGY_SIGNALS_TOPIC, strategyId, signal);
    }

    /**
     * Publish an agent pipeline result event.
     *
     * @param agentId the agent ID
     * @param result  the pipeline result payload
     */
    public void publishAgentPipelineResult(String agentId, Map<String, Object> result) {
        publish(KafkaConfig.AGENT_PIPELINE_EVENTS_TOPIC, agentId, result);
    }

    /**
     * Publish a risk alert event.
     *
     * @param alertType type of risk alert (e.g. VAR_BREACH, POSITION_LIMIT)
     * @param alert     the alert payload
     */
    public void publishRiskAlert(String alertType, Map<String, Object> alert) {
        publish(KafkaConfig.RISK_ALERTS_TOPIC, alertType, alert);
    }

    /**
     * Publish a signal prediction event for IC tracking.
     *
     * @param strategyId the strategy ID
     * @param prediction the prediction payload
     */
    public void publishSignalPrediction(String strategyId, Map<String, Object> prediction) {
        publish(KafkaConfig.SIGNAL_PREDICTIONS_TOPIC, strategyId, prediction);
    }

    /**
     * Publish an order fill event.
     *
     * @param orderId the order ID
     * @param fill    the fill payload
     */
    public void publishOrderFill(String orderId, Map<String, Object> fill) {
        publish(KafkaConfig.ORDER_FILLS_TOPIC, orderId, fill);
    }

    private void publish(String topic, String key, Map<String, Object> data) {
        try {
            String json = objectMapper.writeValueAsString(data);
            kafkaTemplate.send(topic, key, json);
            log.debug("Published to {}: key={}", topic, key);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize event for topic {}: {}", topic, e.getMessage());
        }
    }
}
