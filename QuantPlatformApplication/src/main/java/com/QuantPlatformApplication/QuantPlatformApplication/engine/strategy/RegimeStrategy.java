package com.QuantPlatformApplication.QuantPlatformApplication.engine.strategy;

import com.QuantPlatformApplication.QuantPlatformApplication.engine.model.*;
import com.QuantPlatformApplication.QuantPlatformApplication.engine.util.MathUtils;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Regime Strategy — multi-indicator regime classifier.

 * Uses VIX, yield-curve spread (10Y−2Y), and inflation to classify the market
 * into RISK_ON, RISK_OFF, INFLATIONARY, or RECESSIONARY and allocate
 * accordingly.
 */
@Component
public class RegimeStrategy implements TradingStrategy {

    @Override
    public ModelType getModelType() {
        return ModelType.REGIME;
    }

    @Override
    public ExecutionResult execute(StrategyConfig strategy, MarketData data) {
        double vix = data.getVix();
        double yieldCurve = data.getYieldCurveSpread();
        double inflation = data.getInflationRate();
        double price = data.getCurrentPrice();

        // Determine market regime
        MarketRegime regime;
        Map<String, Double> allocation;

        if (yieldCurve < 0) {
            regime = MarketRegime.RECESSIONARY;
            allocation = Map.of("equity", 25.0, "bonds", 60.0,
                    "commodities", 5.0, "cash", 10.0);
        } else if (vix > 25) {
            regime = MarketRegime.RISK_OFF;
            allocation = Map.of("equity", 30.0, "bonds", 50.0,
                    "commodities", 5.0, "cash", 15.0);
        } else if (inflation > 4.0) {
            regime = MarketRegime.INFLATIONARY;
            allocation = Map.of("equity", 40.0, "bonds", 10.0,
                    "commodities", 40.0, "cash", 10.0);
        } else {
            regime = MarketRegime.RISK_ON;
            allocation = Map.of("equity", 70.0, "bonds", 20.0,
                    "commodities", 5.0, "cash", 5.0);
        }

        // Decide action based on regime
        Action action;
        double confidence;
        String reasoning;

        switch (regime) {
            case RISK_ON -> {
                action = Action.BUY;
                confidence = 60;
                reasoning = String.format("Risk-on regime (VIX=%.1f, curve=%.2f%%). " +
                        "Favorable for equity exposure.", vix, yieldCurve);
            }
            case RISK_OFF -> {
                action = Action.SELL;
                confidence = Math.min(vix * 2, 100);
                reasoning = String.format("Risk-off (VIX=%.1f >25). " +
                        "Shift to bonds + cash.", vix);
            }
            case INFLATIONARY -> {
                action = Action.HOLD;
                confidence = 40;
                reasoning = String.format("Inflationary regime (CPI=%.1f%%). " +
                        "Rotate into commodities + TIPS.", inflation);
            }
            case RECESSIONARY -> {
                action = Action.SELL;
                confidence = 80;
                reasoning = String.format("Inverted yield curve (spread=%.2f%%). " +
                        "Defensive: bonds + cash.", yieldCurve);
            }
            default -> {
                action = Action.HOLD;
                confidence = 0;
                reasoning = "Unknown regime";
            }
        }

        int qty = MathUtils.calculatePositionSize(strategy.getCurrentCash(), price, 0.6);

        Decision decision = new Decision(action, qty, price, reasoning, confidence,
                Map.of("regime", regime.name(), "vix", vix,
                        "yieldCurve", yieldCurve, "inflation", inflation,
                        "allocation", allocation.toString()));

        return ExecutionResult.success(strategy.getId(), decision);
    }
}
