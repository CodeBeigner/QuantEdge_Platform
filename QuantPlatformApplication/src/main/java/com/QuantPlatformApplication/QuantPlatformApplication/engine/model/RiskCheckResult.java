package com.QuantPlatformApplication.QuantPlatformApplication.engine.model;

import lombok.Getter;

import java.util.List;

@Getter
public class RiskCheckResult {

    private final boolean approved;
    private final double positionSize;
    private final double riskAmount;
    private final double effectiveLeverage;
    private final int nominalLeverage;
    private final List<String> rejectionReasons;

    private RiskCheckResult(boolean approved, double positionSize, double riskAmount,
                            double effectiveLeverage, int nominalLeverage, List<String> rejectionReasons) {
        this.approved = approved;
        this.positionSize = positionSize;
        this.riskAmount = riskAmount;
        this.effectiveLeverage = effectiveLeverage;
        this.nominalLeverage = nominalLeverage;
        this.rejectionReasons = rejectionReasons;
    }

    public static RiskCheckResult approve(double positionSize, double riskAmount,
                                          double effectiveLeverage, int nominalLeverage) {
        return new RiskCheckResult(true, positionSize, riskAmount,
                effectiveLeverage, nominalLeverage, List.of());
    }

    public static RiskCheckResult reject(List<String> reasons) {
        return new RiskCheckResult(false, 0, 0, 0, 0, reasons);
    }
}
