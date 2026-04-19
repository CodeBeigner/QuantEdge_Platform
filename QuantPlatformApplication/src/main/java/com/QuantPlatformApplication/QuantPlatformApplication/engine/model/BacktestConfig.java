package com.QuantPlatformApplication.QuantPlatformApplication.engine.model;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class BacktestConfig {
    @Builder.Default private final double initialCapital = 500;
    @Builder.Default private final double slippageBps = 10;         // 10 bps for crypto (realistic)
    @Builder.Default private final double makerFeePct = 0.0002;     // 0.02%
    @Builder.Default private final double takerFeePct = 0.0005;     // 0.05%
    @Builder.Default private final double fundingRatePer8h = 0.0001; // 0.01% default
    @Builder.Default private final boolean useMakerOrders = true;    // prefer maker fees
    @Builder.Default private final RiskParameters riskParameters = RiskParameters.builder().build();
}
