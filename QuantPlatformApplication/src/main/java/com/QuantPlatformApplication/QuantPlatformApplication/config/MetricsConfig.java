package com.QuantPlatformApplication.QuantPlatformApplication.config;

import io.micrometer.core.aop.TimedAspect;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Enables @Timed annotation support for custom metrics.
 * Registers the TimedAspect bean that intercepts @Timed annotated methods
 * and records their execution time to Prometheus.
 */
@Configuration
public class MetricsConfig {

    @Bean
    public TimedAspect timedAspect(MeterRegistry registry) {
        return new TimedAspect(registry);
    }
}
