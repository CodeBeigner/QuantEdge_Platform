package com.QuantPlatformApplication.QuantPlatformApplication.model.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class RiskConfigRequest {
    private BigDecimal riskPerTradePct;
    private BigDecimal maxEffectiveLeverage;
    private BigDecimal dailyLossHaltPct;
    private BigDecimal maxDrawdownPct;
    private Integer maxConcurrentPositions;
    private BigDecimal maxStopDistancePct;
    private BigDecimal minRiskRewardRatio;
    private BigDecimal feeImpactThreshold;
    private String executionMode;
}
