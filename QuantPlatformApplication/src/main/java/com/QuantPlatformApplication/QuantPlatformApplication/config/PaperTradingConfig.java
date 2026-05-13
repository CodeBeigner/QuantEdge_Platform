package com.QuantPlatformApplication.QuantPlatformApplication.config;

import com.QuantPlatformApplication.QuantPlatformApplication.engine.model.RiskParameters;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for paper trading orchestration.
 * Provides default RiskParameters bean for injection into MarketTickScheduler.
 */
@Configuration
public class PaperTradingConfig {

    @Bean
    public RiskParameters riskParameters() {
        return RiskParameters.builder().build();
    }
}
