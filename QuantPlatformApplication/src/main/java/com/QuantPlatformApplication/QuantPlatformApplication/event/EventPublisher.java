package com.QuantPlatformApplication.QuantPlatformApplication.event;

import com.QuantPlatformApplication.QuantPlatformApplication.config.KafkaConfig;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

/**
 * Publishes events to Kafka topics for async processing and audit logging.
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "spring.kafka.enabled", havingValue = "true", matchIfMissing = false)
public class EventPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public EventPublisher(KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    public void publishStrategyExecution(StrategyExecutionEvent event) {
        try {
            String json = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(KafkaConfig.STRATEGY_EXECUTIONS_TOPIC,
                    String.valueOf(event.getStrategyId()), json);
            log.debug("Published execution event for strategy {}", event.getStrategyId());
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize execution event: {}", e.getMessage());
        }
    }
}
