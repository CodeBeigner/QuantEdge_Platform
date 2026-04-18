package com.QuantPlatformApplication.QuantPlatformApplication.service.risk;

import com.QuantPlatformApplication.QuantPlatformApplication.engine.model.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Slf4j
@Component
public class TradeRiskEngine {

    public RiskCheckResult evaluate(TradeRequest request, double currentBalance, double peakEquity,
                                    double currentExposure, double dailyRealizedLoss,
                                    Set<String> openPositionSymbols, RiskParameters params) {

        List<String> rejections = new ArrayList<>();

        // CHECK 6: Stop-loss validation (run first — other checks depend on stop distance)
        double stopDistance = validateStopLoss(request, params, rejections);
        if (stopDistance <= 0 && !rejections.isEmpty()) {
            return RiskCheckResult.reject(rejections);
        }

        // CHECK 1: Position sizing
        double riskAmount = currentBalance * params.getRiskPerTradePct();
        double positionSize = riskAmount / stopDistance;
        double notionalValue = positionSize * request.getEntryPrice();

        // CHECK: Risk-reward ratio
        double rewardDistance = Math.abs(request.getTakeProfitPrice() - request.getEntryPrice());
        double rrRatio = rewardDistance / stopDistance;
        if (rrRatio < params.getMinRiskRewardRatio()) {
            rejections.add(String.format("Risk:reward ratio %.2f below minimum %.1f",
                    rrRatio, params.getMinRiskRewardRatio()));
        }

        // CHECK 2: Effective leverage
        double newTotalExposure = currentExposure + notionalValue;
        double effectiveLeverage = newTotalExposure / currentBalance;
        if (effectiveLeverage > params.getMaxEffectiveLeverage()) {
            rejections.add(String.format("Effective leverage %.1fx exceeds max %.1fx",
                    effectiveLeverage, params.getMaxEffectiveLeverage()));
        }

        // CHECK 3: Daily loss limit
        double dailyLossLimit = peakEquity * params.getDailyLossHaltPct();
        if (dailyRealizedLoss >= dailyLossLimit) {
            rejections.add(String.format("Daily loss $%.2f hit daily loss limit $%.2f (%.0f%% of starting balance)",
                    dailyRealizedLoss, dailyLossLimit, params.getDailyLossHaltPct() * 100));
        }

        // CHECK 4: Max drawdown
        double drawdownPct = (peakEquity - currentBalance) / peakEquity;
        if (drawdownPct >= params.getMaxDrawdownPct()) {
            rejections.add(String.format("Drawdown %.1f%% exceeds max %.0f%%",
                    drawdownPct * 100, params.getMaxDrawdownPct() * 100));
        }

        // CHECK 5: Concurrent positions
        if (openPositionSymbols.size() >= params.getMaxConcurrentPositions()) {
            rejections.add(String.format("Already %d concurrent positions (max %d)",
                    openPositionSymbols.size(), params.getMaxConcurrentPositions()));
        }
        if (openPositionSymbols.contains(request.getSymbol())) {
            rejections.add(String.format("Already have open position in %s", request.getSymbol()));
        }

        // CHECK 7: Fee impact
        double estimatedFees = notionalValue * params.getEstimatedRoundTripFeePct();
        double feeImpact = riskAmount > 0 ? estimatedFees / riskAmount : Double.MAX_VALUE;
        if (feeImpact > params.getFeeImpactThreshold()) {
            rejections.add(String.format("Fees $%.2f are %.0f%% of risk $%.2f (max %.0f%%)",
                    estimatedFees, feeImpact * 100, riskAmount, params.getFeeImpactThreshold() * 100));
        }

        if (!rejections.isEmpty()) {
            log.warn("Trade REJECTED for {}: {}", request.getSymbol(), rejections);
            return RiskCheckResult.reject(rejections);
        }

        int nominalLeverage = calculateNominalLeverage(notionalValue, currentBalance);

        log.info("Trade APPROVED for {}: size={}, risk=${}, effLev={}x, nomLev={}x",
                request.getSymbol(), positionSize, riskAmount, effectiveLeverage, nominalLeverage);

        return RiskCheckResult.approve(positionSize, riskAmount, effectiveLeverage, nominalLeverage);
    }

    private double validateStopLoss(TradeRequest request, RiskParameters params,
                                     List<String> rejections) {
        if (request.getStopLossPrice() <= 0) {
            rejections.add("Stop-loss is mandatory — no trade without a stop");
            return 0;
        }

        boolean isLong = request.getAction() == Action.BUY;
        double stopDistance;

        if (isLong) {
            if (request.getStopLossPrice() >= request.getEntryPrice()) {
                rejections.add("Stop-loss must be below entry for BUY orders");
                return 0;
            }
            stopDistance = request.getEntryPrice() - request.getStopLossPrice();
        } else {
            if (request.getStopLossPrice() <= request.getEntryPrice()) {
                rejections.add("Stop-loss must be above entry for SELL orders");
                return 0;
            }
            stopDistance = request.getStopLossPrice() - request.getEntryPrice();
        }

        double stopPct = stopDistance / request.getEntryPrice();
        if (stopPct > params.getMaxStopDistancePct()) {
            rejections.add(String.format("Stop distance %.2f%% exceeds max %.0f%%",
                    stopPct * 100, params.getMaxStopDistancePct() * 100));
        }

        return stopDistance;
    }

    private int calculateNominalLeverage(double notionalValue, double balance) {
        double rawLeverage = notionalValue / balance;
        if (rawLeverage <= 1) return 10;
        if (rawLeverage <= 3) return 10;
        if (rawLeverage <= 5) return 10;
        if (rawLeverage <= 10) return 15;
        if (rawLeverage <= 15) return 20;
        return 25;
    }
}
