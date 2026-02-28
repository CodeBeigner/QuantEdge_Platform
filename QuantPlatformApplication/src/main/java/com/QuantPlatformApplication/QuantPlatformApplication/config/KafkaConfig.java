package com.QuantPlatformApplication.QuantPlatformApplication.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Kafka topic configuration.
 * Topics are auto-created on startup.
 */
@Configuration
@ConditionalOnProperty(name = "spring.kafka.enabled", havingValue = "true", matchIfMissing = false)
public class KafkaConfig {

    public static final String STRATEGY_EXECUTIONS_TOPIC = "strategy-executions";
    public static final String MARKET_DATA_TOPIC = "market-data-updates";

    @Bean
    public NewTopic strategyExecutionsTopic() {
        return TopicBuilder.name(STRATEGY_EXECUTIONS_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic marketDataTopic() {
        return TopicBuilder.name(MARKET_DATA_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
