package com.QuantPlatformApplication.QuantPlatformApplication;

import com.QuantPlatformApplication.QuantPlatformApplication.config.EncryptionConfig;
import com.QuantPlatformApplication.QuantPlatformApplication.service.delta.DeltaExchangeClient;
import com.QuantPlatformApplication.QuantPlatformApplication.service.delta.DeltaExchangeConfig;
import com.QuantPlatformApplication.QuantPlatformApplication.service.pipeline.CandleAggregator;
import com.QuantPlatformApplication.QuantPlatformApplication.service.pipeline.IndicatorCalculator;
import com.QuantPlatformApplication.QuantPlatformApplication.service.risk.TradeRiskEngine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Smoke test verifying all Phase 1 beans wire up correctly.
 * Requires PostgreSQL and Redis running — skip in CI without DB.
 */
@SpringBootTest
class ApplicationSmokeTest {

    @Autowired private CandleAggregator candleAggregator;
    @Autowired private IndicatorCalculator indicatorCalculator;
    @Autowired private TradeRiskEngine tradeRiskEngine;
    @Autowired private DeltaExchangeClient deltaExchangeClient;
    @Autowired private DeltaExchangeConfig deltaExchangeConfig;
    @Autowired private EncryptionConfig encryptionConfig;

    @Test
    void allNewBeansLoad() {
        assertNotNull(candleAggregator);
        assertNotNull(indicatorCalculator);
        assertNotNull(tradeRiskEngine);
        assertNotNull(deltaExchangeClient);
        assertNotNull(deltaExchangeConfig);
        assertNotNull(encryptionConfig);
    }
}
