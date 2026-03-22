package com.QuantPlatformApplication.QuantPlatformApplication.engine;

import com.QuantPlatformApplication.QuantPlatformApplication.engine.model.*;
import com.QuantPlatformApplication.QuantPlatformApplication.engine.strategy.TradingStrategy;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * Backtesting engine — replays a strategy over historical price data
 * day-by-day.
 *
 * Tracks: equity curve, positions, PnL, trades, transaction costs.
 * Calculates: Sharpe ratio, max drawdown, win rate, total return.
 *
 * BUG 3 FIX: Applies slippage (3bps default) to all BUY/SELL fills
 * and tracks cumulative transaction costs as a metric.
 */
@Slf4j
public class BacktestEngine {

    /** Default slippage in basis points applied to every fill */
    private static final double DEFAULT_SLIPPAGE_BPS = 3.0 / 10000.0;

    /** Minimum lookback to divide prices */
    private static final int MIN_LOOKBACK_DIVISOR = 2;

    /** Default lookback window size */
    private static final int DEFAULT_LOOKBACK = 50;

    /** Default train window for walk-forward (1 year of trading days) */
    private static final int DEFAULT_TRAIN_WINDOW_DAYS = 252;

    /** Default test window for walk-forward (1 quarter of trading days) */
    private static final int DEFAULT_TEST_WINDOW_DAYS = 63;

    /** Walk-forward: max acceptable std dev of Sharpe across windows */
    private static final double WALK_FORWARD_SHARPE_STD_THRESHOLD = 0.5;

    /** Walk-forward: min acceptable mean Sharpe across windows */
    private static final double WALK_FORWARD_MEAN_SHARPE_THRESHOLD = 0.5;

    /**
     * Run a backtest for a given strategy over historical prices.
     *
     * @param strategy The trading strategy to test
     * @param config   Strategy configuration (cash, risk, etc.)
     * @param prices   Chronological list of daily close prices
     * @return BacktestMetrics with equity curve, performance stats, and transaction costs
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
        double transactionCosts = 0;

        // We need a lookback window; start from day 50
        int lookback = Math.min(DEFAULT_LOOKBACK, prices.size() / MIN_LOOKBACK_DIVISOR);

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
                        // BUG 3 FIX: Apply slippage to buy fill price
                        double fillPrice = currentPrice * (1 + DEFAULT_SLIPPAGE_BPS);
                        int qty = Math.min(decision.quantity(), (int) (cash / fillPrice));
                        if (qty > 0) {
                            double slippageCost = qty * (fillPrice - currentPrice);
                            transactionCosts += slippageCost;
                            position += qty;
                            cash -= qty * fillPrice;
                            totalTrades++;
                        }
                    }
                    case SELL -> {
                        // BUG 3 FIX: Apply slippage to sell fill price
                        double fillPrice = currentPrice * (1 - DEFAULT_SLIPPAGE_BPS);
                        int qty = Math.min(decision.quantity(), position);
                        if (qty > 0) {
                            double slippageCost = qty * (currentPrice - fillPrice);
                            transactionCosts += slippageCost;
                            double costBasis = config.getCurrentCash() / (position > 0 ? position : 1);
                            if (fillPrice > costBasis)
                                winningTrades++;
                            position -= qty;
                            cash += qty * fillPrice;
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
                sharpe, maxDrawdown, winRate, totalTrades, equityCurve, transactionCosts);
    }

    /**
     * Run walk-forward validation for a strategy.
     *
     * <p>Slides a train/test window across the price data:
     * train on [start, start+trainWindowDays], test on [start+trainWindowDays, start+trainWindowDays+testWindowDays].
     * Advances by testWindowDays each iteration.
     *
     * @param strategy         The trading strategy to test
     * @param config           Strategy configuration
     * @param prices           Chronological list of daily close prices
     * @param trainWindowDays  Number of days in each training window (e.g. 252)
     * @param testWindowDays   Number of days in each test window (e.g. 63)
     * @return WalkForwardResult with per-window metrics and aggregate stats
     */
    public static WalkForwardResult runWalkForward(
            TradingStrategy strategy,
            StrategyConfig config,
            List<Double> prices,
            int trainWindowDays,
            int testWindowDays) {

        List<BacktestMetrics> windows = new ArrayList<>();
        int totalDataPoints = prices.size();

        int windowStart = 0;
        while (windowStart + trainWindowDays + testWindowDays <= totalDataPoints) {
            // Test window prices include train data for lookback context
            int testStart = windowStart + trainWindowDays;
            int testEnd = testStart + testWindowDays;

            // Run backtest on just the test window prices, but with full train period for lookback
            List<Double> windowPrices = prices.subList(windowStart, testEnd);
            BacktestMetrics metrics = run(strategy, config, windowPrices);
            windows.add(metrics);

            windowStart += testWindowDays;
        }

        if (windows.isEmpty()) {
            return new WalkForwardResult(
                    windows, 0, 0, 0, 0, false, 0);
        }

        // Compute aggregate statistics
        double sumSharpe = 0;
        double sumDrawdown = 0;
        double worstDrawdown = 0;

        for (BacktestMetrics w : windows) {
            sumSharpe += w.getSharpeRatio();
            sumDrawdown += w.getMaxDrawdown();
            worstDrawdown = Math.max(worstDrawdown, w.getMaxDrawdown());
        }

        double meanSharpe = sumSharpe / windows.size();
        double meanDrawdown = sumDrawdown / windows.size();

        // Standard deviation of Sharpe
        double sumSqDiff = 0;
        for (BacktestMetrics w : windows) {
            double diff = w.getSharpeRatio() - meanSharpe;
            sumSqDiff += diff * diff;
        }
        double stdDevSharpe = Math.sqrt(sumSqDiff / windows.size());

        // Robustness verdict
        boolean isRobust = stdDevSharpe < WALK_FORWARD_SHARPE_STD_THRESHOLD
                && meanSharpe > WALK_FORWARD_MEAN_SHARPE_THRESHOLD;

        return new WalkForwardResult(
                windows, meanSharpe, stdDevSharpe, meanDrawdown,
                worstDrawdown, isRobust, windows.size());
    }

    /**
     * Calculate annualized Sharpe ratio from daily returns.
     *
     * @param returns list of daily returns
     * @return annualized Sharpe ratio (0 if insufficient data)
     */
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

    /**
     * Backtest performance metrics for a single run.
     */
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
        private final double transactionCosts;

        public BacktestMetrics(double initialCapital, double finalCapital, double totalReturn,
                double sharpeRatio, double maxDrawdown, double winRate,
                int totalTrades, List<Double> equityCurve, double transactionCosts) {
            this.initialCapital = initialCapital;
            this.finalCapital = finalCapital;
            this.totalReturn = totalReturn;
            this.sharpeRatio = sharpeRatio;
            this.maxDrawdown = maxDrawdown;
            this.winRate = winRate;
            this.totalTrades = totalTrades;
            this.equityCurve = equityCurve;
            this.transactionCosts = transactionCosts;
        }
    }

    /**
     * Walk-forward validation results aggregating multiple test windows.
     */
    @Getter
    public static class WalkForwardResult {
        private final List<BacktestMetrics> windows;
        private final double meanSharpe;
        private final double stdDevSharpe;
        private final double meanDrawdown;
        private final double worstDrawdown;
        private final boolean isRobust;
        private final int windowCount;

        public WalkForwardResult(List<BacktestMetrics> windows,
                double meanSharpe, double stdDevSharpe,
                double meanDrawdown, double worstDrawdown,
                boolean isRobust, int windowCount) {
            this.windows = windows;
            this.meanSharpe = meanSharpe;
            this.stdDevSharpe = stdDevSharpe;
            this.meanDrawdown = meanDrawdown;
            this.worstDrawdown = worstDrawdown;
            this.isRobust = isRobust;
            this.windowCount = windowCount;
        }
    }
}
