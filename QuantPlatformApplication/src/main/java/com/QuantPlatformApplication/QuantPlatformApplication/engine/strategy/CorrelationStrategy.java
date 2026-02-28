package com.QuantPlatformApplication.QuantPlatformApplication.engine.strategy;

import com.QuantPlatformApplication.QuantPlatformApplication.engine.model.*;
import com.QuantPlatformApplication.QuantPlatformApplication.engine.util.MathUtils;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Correlation Strategy — rolling pairwise correlation.

 * SELL when |correlation| > 0.85 (concentration risk)
 * BUY when |correlation| < 0.30 (strong diversification)
 * HOLD otherwise
 */
@Component
public class CorrelationStrategy implements TradingStrategy {

    @Override
    public ModelType getModelType() {
        return ModelType.CORRELATION;
    }

    @Override
    public ExecutionResult execute(StrategyConfig strategy, MarketData data) {
        List<Double> returns1 = MathUtils.calculateReturns(data.getPrices());
        List<Double> returns2 = MathUtils.calculateReturns(data.getSecondaryPrices());
        double price = data.getCurrentPrice();

        int corrWindow = 60;
        double correlation = MathUtils.calculateCorrelation(returns1, returns2, corrWindow);

        Action action;
        double confidence;
        String reasoning;

        if (Math.abs(correlation) > 0.85) {
            action = Action.SELL;
            confidence = Math.min(Math.abs(correlation) * 100, 100);
            reasoning = String.format("Correlation = %.3f → concentration risk. " +
                    "Assets moving in lockstep; reduce combined exposure.", correlation);
        } else if (Math.abs(correlation) < 0.3) {
            action = Action.BUY;
            confidence = Math.min((1.0 - Math.abs(correlation)) * 50, 100);
            reasoning = String.format("Correlation = %.3f → strong diversification benefit. " +
                    "Safe to hold or increase combined positions.", correlation);
        } else {
            action = Action.HOLD;
            confidence = 0;
            reasoning = String.format("Correlation = %.3f → moderate. " +
                    "Keep monitoring for regime shifts.", correlation);
        }

        int qty = MathUtils.calculatePositionSize(strategy.getCurrentCash(), price, 0.8);

        Decision decision = new Decision(action, qty, price, reasoning, confidence,
                Map.of("correlation60d", correlation,
                        "diversificationStrength",
                        Math.abs(correlation) < 0.5 ? "STRONG" : "WEAK"));

        return ExecutionResult.success(strategy.getId(), decision);
    }
}
