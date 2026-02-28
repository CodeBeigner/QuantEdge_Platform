package com.QuantPlatformApplication.QuantPlatformApplication.engine.strategy;

import com.QuantPlatformApplication.QuantPlatformApplication.engine.model.*;
import com.QuantPlatformApplication.QuantPlatformApplication.engine.util.MathUtils;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Volatility Strategy — vol-scaled position sizing.

 * BUY when price > SMA20 AND volatility is below 1.5× average
 * SELL when volatility spikes above 2× average
 * HOLD otherwise

 * Position size is inversely proportional to current volatility.
 */
@Component
public class VolatilityStrategy implements TradingStrategy {

    @Override
    public ModelType getModelType() {
        return ModelType.VOLATILITY;
    }

    @Override
    public ExecutionResult execute(StrategyConfig strategy, MarketData data) {
        List<Double> returns = MathUtils.calculateReturns(data.getPrices());
        double currentVol = MathUtils.calculateVolatility(returns, 20);
        List<Double> rollingVols = MathUtils.calculateRollingVolatility(returns, 20);
        double avgVol = MathUtils.calculateAverage(rollingVols);
        double sma20 = MathUtils.calculateSMA(data.getPrices(), 20);
        double price = data.getCurrentPrice();

        Signal signal = generateSignal(price, sma20, currentVol, avgVol);

        // Position ∝ 1/Volatility
        int qty = calculateVolAdjustedPosition(strategy.getTargetRisk(), price, currentVol);

        Decision decision = new Decision(signal.action(), qty, price,
                signal.reasoning(), signal.confidence(),
                Map.of("volatility", currentVol, "avgVolatility", avgVol,
                        "volRatio", avgVol > 0 ? currentVol / avgVol : 0));

        return ExecutionResult.success(strategy.getId(), decision);
    }

    private Signal generateSignal(double price, double sma20,
            double currentVol, double avgVol) {
        if (avgVol == 0)
            return new Signal(Action.HOLD, 0, "Insufficient vol history");

        double a = Math.abs(currentVol - avgVol) / avgVol * 100;
        if (price > sma20 && currentVol < avgVol * 1.5) {
            return new Signal(Action.BUY,
                    Math.min(a, 100),
                    String.format("Low-vol uptrend: Vol %.1f%% < AvgVol %.1f%%", currentVol, avgVol));
        }
        if (currentVol > avgVol * 2) {
            return new Signal(Action.SELL,
                    Math.min(a, 100),
                    String.format("High vol spike: Vol %.1f%% >> AvgVol %.1f%%, reducing exposure",
                            currentVol, avgVol));
        }
        return new Signal(Action.HOLD, 0, "Vol within normal range");
    }

    private int calculateVolAdjustedPosition(double targetRisk, double price, double vol) {
        if (vol <= 0 || price <= 0)
            return 0;
        double base = targetRisk / (vol / 100.0);
        return (int) Math.floor(base / price);
    }
}
