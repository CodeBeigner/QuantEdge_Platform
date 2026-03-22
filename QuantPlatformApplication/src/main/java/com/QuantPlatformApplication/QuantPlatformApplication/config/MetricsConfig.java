package com.QuantPlatformApplication.QuantPlatformApplication.config;

import io.micrometer.core.aop.TimedAspect;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Enables @Timed annotation support and registers custom AI agent metrics
 * for Prometheus monitoring.
 *
 * <p>Metrics registered:
 * <ul>
 *   <li>{@code quantedge_agent_pipeline_total} — Counter of pipeline executions</li>
 *   <li>{@code quantedge_agent_pipeline_failures_total} — Counter of failed pipelines</li>
 *   <li>{@code quantedge_agent_pipeline_duration} — Timer for pipeline execution time</li>
 *   <li>{@code quantedge_active_agents} — Gauge of currently active agents</li>
 *   <li>{@code quantedge_signal_drift_alerts_total} — Counter of drift alerts</li>
 * </ul>
 */
@Configuration
public class MetricsConfig {

    private final AtomicInteger activeAgentsCount = new AtomicInteger(0);

    @Bean
    public TimedAspect timedAspect(MeterRegistry registry) {
        return new TimedAspect(registry);
    }

    @Bean
    public Counter agentPipelineCounter(MeterRegistry registry) {
        return Counter.builder("quantedge_agent_pipeline_total")
                .description("Total number of AI agent pipeline executions")
                .register(registry);
    }

    @Bean
    public Counter agentPipelineFailureCounter(MeterRegistry registry) {
        return Counter.builder("quantedge_agent_pipeline_failures_total")
                .description("Total number of failed AI agent pipeline executions")
                .register(registry);
    }

    @Bean
    public Timer agentPipelineDuration(MeterRegistry registry) {
        return Timer.builder("quantedge_agent_pipeline_duration")
                .description("Duration of AI agent pipeline executions")
                .register(registry);
    }

    @Bean
    public AtomicInteger activeAgentsGauge(MeterRegistry registry) {
        Gauge.builder("quantedge_active_agents", activeAgentsCount, AtomicInteger::get)
                .description("Number of currently active trading agents")
                .register(registry);
        return activeAgentsCount;
    }

    @Bean
    public Counter signalDriftAlertCounter(MeterRegistry registry) {
        return Counter.builder("quantedge_signal_drift_alerts_total")
                .description("Total number of signal drift alerts triggered")
                .register(registry);
    }
}
