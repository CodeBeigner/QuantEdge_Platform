package com.QuantPlatformApplication.QuantPlatformApplication.engine.model;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class RiskParameters {
    @Builder.Default private final double riskPerTradePct = 0.01;
    @Builder.Default private final double maxEffectiveLeverage = 5.0;
    @Builder.Default private final double dailyLossHaltPct = 0.05;
    @Builder.Default private final double maxDrawdownPct = 0.15;
    @Builder.Default private final int maxConcurrentPositions = 3;
    @Builder.Default private final double maxStopDistancePct = 0.02;
    @Builder.Default private final double minRiskRewardRatio = 1.5;
    @Builder.Default private final double feeImpactThreshold = 0.20;
    @Builder.Default private final double estimatedRoundTripFeePct = 0.001;
}
