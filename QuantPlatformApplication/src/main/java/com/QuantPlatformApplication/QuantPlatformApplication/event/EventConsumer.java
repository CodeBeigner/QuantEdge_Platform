package com.QuantPlatformApplication.QuantPlatformApplication.event;

import com.QuantPlatformApplication.QuantPlatformApplication.config.KafkaConfig;
import com.QuantPlatformApplication.QuantPlatformApplication.service.SignalTrackingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Consumes domain events from Kafka topics.
 *
 * <p>Handles order fill events to trigger signal IC resolution,
 * and logs other events for monitoring.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EventConsumer {

    private final SignalTrackingService signalTrackingService;
    private final ObjectMapper objectMapper;

    /**
     * Handle order fill events — resolve signal predictions with actual returns.
     *
     * @param message JSON message containing fill details
     */
    @KafkaListener(topics = KafkaConfig.ORDER_FILLS_TOPIC, groupId = "quantedge-signal-tracker")
    public void handleOrderFill(String message) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> fill = objectMapper.readValue(message, Map.class);
            Long predictionId = Long.valueOf(fill.getOrDefault("predictionId", 0L).toString());
            double actualReturn = Double.parseDouble(fill.getOrDefault("actualReturn", 0.0).toString());

            if (predictionId > 0) {
                signalTrackingService.recordActualReturn(predictionId, actualReturn);
                log.info("Resolved signal prediction: predictionId={}, actualReturn={}",
                        predictionId, actualReturn);
            }
        } catch (Exception e) {
            log.error("Failed to process order fill event: {}", e.getMessage(), e);
        }
    }

    /**
     * Handle risk alert events — log them for monitoring dashboards.
     *
     * @param message JSON message containing risk alert details
     */
    @KafkaListener(topics = KafkaConfig.RISK_ALERTS_TOPIC, groupId = "quantedge-risk-monitor")
    public void handleRiskAlert(String message) {
        log.warn("Risk alert received: {}", message);
    }

    /**
     * Handle agent pipeline events — log for pipeline monitoring.
     *
     * @param message JSON message containing pipeline results
     */
    @KafkaListener(topics = KafkaConfig.AGENT_PIPELINE_EVENTS_TOPIC, groupId = "quantedge-pipeline-monitor")
    public void handleAgentPipelineEvent(String message) {
        log.info("Agent pipeline event received: {}", message);
    }
}
