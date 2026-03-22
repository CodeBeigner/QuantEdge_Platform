package com.QuantPlatformApplication.QuantPlatformApplication.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Kafka topic configuration.
 * Creates all required topics on application startup.
 */
@Configuration
public class KafkaConfig {

    public static final String MARKET_DATA_TOPIC = "market-data";
    public static final String STRATEGY_SIGNALS_TOPIC = "strategy-signals";
    public static final String AGENT_PIPELINE_EVENTS_TOPIC = "agent-pipeline-events";
    public static final String RISK_ALERTS_TOPIC = "risk-alerts";
    public static final String SIGNAL_PREDICTIONS_TOPIC = "signal-predictions";
    public static final String ORDER_FILLS_TOPIC = "order-fills";

    @Bean
    public NewTopic marketDataTopic() {
        return TopicBuilder.name(MARKET_DATA_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic strategySignalsTopic() {
        return TopicBuilder.name(STRATEGY_SIGNALS_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic agentPipelineEventsTopic() {
        return TopicBuilder.name(AGENT_PIPELINE_EVENTS_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic riskAlertsTopic() {
        return TopicBuilder.name(RISK_ALERTS_TOPIC)
                .partitions(1)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic signalPredictionsTopic() {
        return TopicBuilder.name(SIGNAL_PREDICTIONS_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic orderFillsTopic() {
        return TopicBuilder.name(ORDER_FILLS_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
