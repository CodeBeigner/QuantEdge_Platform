package com.QuantPlatformApplication.QuantPlatformApplication.engine.strategy;

import com.QuantPlatformApplication.QuantPlatformApplication.engine.model.*;
import com.QuantPlatformApplication.QuantPlatformApplication.engine.util.MathUtils;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Momentum Strategy — SMA crossover (Golden Cross / Death Cross).

 * BUY when Price > SMA200 AND SMA50 > SMA200 (Golden Cross)
 * SELL when Price < SMA200 AND SMA50 < SMA200 (Death Cross)
 * HOLD otherwise
 */
@Component
public class MomentumStrategy implements TradingStrategy {

    @Override
    public ModelType getModelType() {
        return ModelType.MOMENTUM;
    }

    @Override
    public ExecutionResult execute(StrategyConfig strategy, MarketData data) {
        double sma50 = MathUtils.calculateSMA(data.getPrices(), 50);
        double sma200 = MathUtils.calculateSMA(data.getPrices(), 200);
        double price = data.getCurrentPrice();

        Signal signal = generateSignal(price, sma50, sma200);

        int qty = MathUtils.calculatePositionSize(
                strategy.getCurrentCash(), price, strategy.getPositionMultiplier());

        Decision decision = new Decision(signal.action(), qty, price,
                signal.reasoning(), signal.confidence(), Map.of(
                        "sma50", sma50, "sma200", sma200));

        return ExecutionResult.success(strategy.getId(), decision);
    }

    private Signal generateSignal(double price, double sma50, double sma200) {
        if (sma200 == 0)
            return new Signal(Action.HOLD, 0, "Insufficient data for SMA200");

        if (price > sma200 && sma50 > sma200) {
            double conf = Math.min(((price - sma200) / sma200) * 100, 100);
            return new Signal(Action.BUY, conf,
                    String.format("Uptrend: Price %.2f > SMA200 %.2f, Golden Cross", price, sma200));
        }
        if (price < sma200 && sma50 < sma200) {
            double conf = Math.min(((sma200 - price) / sma200) * 100, 100);
            return new Signal(Action.SELL, conf,
                    String.format("Downtrend: Price %.2f < SMA200 %.2f, Death Cross", price, sma200));
        }
        return new Signal(Action.HOLD, 0, "Mixed signals — no clear trend");
    }
}
