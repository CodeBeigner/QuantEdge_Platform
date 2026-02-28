package com.QuantPlatformApplication.QuantPlatformApplication.engine;

import com.QuantPlatformApplication.QuantPlatformApplication.engine.model.*;
import com.QuantPlatformApplication.QuantPlatformApplication.engine.strategy.TradingStrategy;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Backtesting engine — replays a strategy over historical price data
 * day-by-day.

 * Tracks: equity curve, positions, PnL, trades.
 * Calculates: Sharpe ratio, max drawdown, win rate, total return.
 */
@Slf4j
public class BacktestEngine {

    /**
     * Run a backtest for a given strategy over historical prices.
     *
     * @param strategy The trading strategy to test
     * @param config   Strategy configuration (cash, risk, etc.)
     * @param prices   Chronological list of daily close prices
     * @return BacktestMetrics with equity curve and performance stats
     */
    public static BacktestMetrics run(TradingStrategy strategy, StrategyConfig config,
            List<Double> prices) {
        double cash = config.getCurrentCash();
        int position = 0;
        List<Double> equityCurve = new ArrayList<>();
        List<Double> dailyReturns = new ArrayList<>();
        int totalTrades = 0;
        int winningTrades = 0;
        double peakEquity = cash;
        double maxDrawdown = 0;

        // We need a lookback window; start from day 50
        int lookback = Math.min(50, prices.size() / 2);

        for (int day = lookback; day < prices.size(); day++) {
            double currentPrice = prices.get(day);

            // Build MarketData snapshot with history up to this day
            MarketData data = new MarketData();
            data.setPrices(prices.subList(0, day + 1));
            data.setCurrentPrice(currentPrice);

            // Execute strategy
            ExecutionResult result = strategy.execute(config, data);

            if (result.isSuccess() && result.getDecision() != null) {
                Decision decision = result.getDecision();

                switch (decision.action()) {
                    case BUY -> {
                        int qty = Math.min(decision.quantity(), (int) (cash / currentPrice));
                        if (qty > 0) {
                            position += qty;
                            cash -= qty * currentPrice;
                            totalTrades++;
                        }
                    }
                    case SELL -> {
                        int qty = Math.min(decision.quantity(), position);
                        if (qty > 0) {
                            double costBasis = config.getCurrentCash() / (position > 0 ? position : 1);
                            if (currentPrice > costBasis)
                                winningTrades++;
                            position -= qty;
                            cash += qty * currentPrice;
                            totalTrades++;
                        }
                    }
                    case HOLD -> {
                        /* no-op */ }
                }
            }

            // Calculate equity
            double equity = cash + position * currentPrice;
            equityCurve.add(equity);

            // Daily return
            if (equityCurve.size() > 1) {
                double prevEquity = equityCurve.get(equityCurve.size() - 2);
                dailyReturns.add((equity - prevEquity) / prevEquity);
            }

            // Max drawdown
            peakEquity = Math.max(peakEquity, equity);
            double drawdown = (peakEquity - equity) / peakEquity;
            maxDrawdown = Math.max(maxDrawdown, drawdown);
        }

        double finalEquity = equityCurve.isEmpty() ? cash : equityCurve.getLast();
        double totalReturn = (finalEquity - config.getCurrentCash()) / config.getCurrentCash();
        double sharpe = calculateSharpe(dailyReturns);
        double winRate = totalTrades > 0 ? (double) winningTrades / totalTrades * 100 : 0;

        return new BacktestMetrics(
                config.getCurrentCash(), finalEquity, totalReturn,
                sharpe, maxDrawdown, winRate, totalTrades, equityCurve);
    }

    private static double calculateSharpe(List<Double> returns) {
        if (returns.size() < 2)
            return 0;

        double mean = returns.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double variance = returns.stream()
                .mapToDouble(r -> Math.pow(r - mean, 2))
                .average().orElse(0);
        double stdDev = Math.sqrt(variance);

        if (stdDev == 0)
            return 0;
        return (mean / stdDev) * Math.sqrt(252); // annualized
    }

    @Getter
    public static class BacktestMetrics {
        private final double initialCapital;
        private final double finalCapital;
        private final double totalReturn;
        private final double sharpeRatio;
        private final double maxDrawdown;
        private final double winRate;
        private final int totalTrades;
        private final List<Double> equityCurve;

        public BacktestMetrics(double initialCapital, double finalCapital, double totalReturn,
                double sharpeRatio, double maxDrawdown, double winRate,
                int totalTrades, List<Double> equityCurve) {
            this.initialCapital = initialCapital;
            this.finalCapital = finalCapital;
            this.totalReturn = totalReturn;
            this.sharpeRatio = sharpeRatio;
            this.maxDrawdown = maxDrawdown;
            this.winRate = winRate;
            this.totalTrades = totalTrades;
            this.equityCurve = equityCurve;
        }
    }
}
