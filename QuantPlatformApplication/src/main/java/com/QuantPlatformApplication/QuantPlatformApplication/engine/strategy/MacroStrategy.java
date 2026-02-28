package com.QuantPlatformApplication.QuantPlatformApplication.engine.strategy;

import com.QuantPlatformApplication.QuantPlatformApplication.engine.model.*;
import com.QuantPlatformApplication.QuantPlatformApplication.engine.util.MathUtils;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Macro Strategy — rate & inflation driven allocation.

 * Classifies the macro environment into TIGHTENING, EASING, INFLATIONARY, or
 * NEUTRAL
 * and adjusts allocations across growth, value, commodities, bonds, and cash
 * accordingly.
 */
@Component
public class MacroStrategy implements TradingStrategy {

    @Override
    public ModelType getModelType() {
        return ModelType.MACRO;
    }

    @Override
    public ExecutionResult execute(StrategyConfig strategy, MarketData data) {
        double interestRate = data.getInterestRate();
        double inflation = data.getInflationRate();
        double price = data.getCurrentPrice();

        // Determine macro regime
        String macroRegime;
        Map<String, Double> allocation;

        if (interestRate > 4.0 && inflation > 3.0) {
            macroRegime = "TIGHTENING";
            allocation = Map.of(
                    "growth", 0.20, "value", 0.35,
                    "commodities", 0.25, "bonds", 0.10, "cash", 0.10);
        } else if (interestRate < 2.0 && inflation < 2.0) {
            macroRegime = "EASING";
            allocation = Map.of(
                    "growth", 0.45, "value", 0.20,
                    "commodities", 0.10, "bonds", 0.15, "cash", 0.10);
        } else if (inflation > 4.0) {
            macroRegime = "INFLATIONARY";
            allocation = Map.of(
                    "growth", 0.15, "value", 0.25,
                    "commodities", 0.35, "bonds", 0.05, "cash", 0.20);
        } else {
            macroRegime = "NEUTRAL";
            allocation = Map.of(
                    "growth", 0.30, "value", 0.25,
                    "commodities", 0.15, "bonds", 0.20, "cash", 0.10);
        }

        // Determine action based on regime
        Action action;
        double confidence;
        String reasoning;

        if (macroRegime.equals("TIGHTENING")) {
            action = Action.SELL;
            confidence = Math.min((interestRate - 3.0) * 25, 100);
            reasoning = String.format("Tightening cycle (rate=%.1f%%, CPI=%.1f%%). " +
                    "Reduce duration, favor value + commodities.", interestRate, inflation);
        } else if (macroRegime.equals("EASING")) {
            action = Action.BUY;
            confidence = Math.min((3.0 - interestRate) * 25, 100);
            reasoning = String.format("Easing cycle (rate=%.1f%%, CPI=%.1f%%). " +
                    "Increase growth + duration exposure.", interestRate, inflation);
        } else {
            action = Action.HOLD;
            confidence = 20;
            reasoning = String.format("Macro regime: %s (rate=%.1f%%, CPI=%.1f%%). " +
                    "No strong directional bias.", macroRegime, interestRate, inflation);
        }

        int qty = MathUtils.calculatePositionSize(strategy.getCurrentCash(), price, 0.5);

        Decision decision = new Decision(action, qty, price, reasoning, confidence,
                Map.of("macroRegime", macroRegime, "allocation", allocation.toString(),
                        "interestRate", interestRate, "inflation", inflation));

        return ExecutionResult.success(strategy.getId(), decision);
    }
}
