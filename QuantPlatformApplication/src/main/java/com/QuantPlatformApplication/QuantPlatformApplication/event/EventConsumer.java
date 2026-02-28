package com.QuantPlatformApplication.QuantPlatformApplication.event;

import com.QuantPlatformApplication.QuantPlatformApplication.config.KafkaConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

/**
 * Kafka consumer — logs strategy execution events.
 * In production, this could persist to an audit table or send notifications.
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "spring.kafka.enabled", havingValue = "true", matchIfMissing = false)
public class EventConsumer {

    @KafkaListener(topics = KafkaConfig.STRATEGY_EXECUTIONS_TOPIC, groupId = "quant-platform")
    public void onStrategyExecution(String message) {
        log.info("Received execution event: {}", message);
    }
}
