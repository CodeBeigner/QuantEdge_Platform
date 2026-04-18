package com.QuantPlatformApplication.QuantPlatformApplication.engine;

import com.QuantPlatformApplication.QuantPlatformApplication.engine.model.*;
import com.QuantPlatformApplication.QuantPlatformApplication.engine.strategy.MultiTimeFrameStrategy;
import com.QuantPlatformApplication.QuantPlatformApplication.service.pipeline.CandleAggregator;
import com.QuantPlatformApplication.QuantPlatformApplication.service.pipeline.IndicatorCalculator;
import com.QuantPlatformApplication.QuantPlatformApplication.service.risk.TradeRiskEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Realistic multi-timeframe backtesting engine.
 *
 * Steps through 15m candles, builds multi-timeframe data (1h, 4h),
 * runs strategies through the risk engine, and tracks P&L with
 * fees, slippage, and funding costs.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MultiTimeFrameBacktestEngine {

    private final CandleAggregator candleAggregator;
    private final IndicatorCalculator indicatorCalculator;
    private final TradeRiskEngine tradeRiskEngine;

    // Minimum 15m candles needed before evaluation starts:
    // 4h candle = 16 x 15m, indicator min = 50 candles of 4h => need 50*16=800
    // But that's too many. Practically: need enough 15m for aggregation + indicator calc.
    // 4h needs 16 x 15m per candle, and IndicatorCalculator needs 50 candles minimum.
    // So we need at least 50 * 16 = 800 for 4h indicators. BUT we use a sliding window
    // of up to 500 15m candles. With 500 15m candles: 500/16 = 31 4h candles (not enough for 50).
    // So 4h indicators will be null until we have enough. We still evaluate if 15m/1h are available.
    // We require at least 64 candles (4 * 16) before starting to evaluate.
    private static final int MIN_CANDLES_BEFORE_EVAL = 64;
    private static final int MAX_WINDOW_SIZE = 500;
    private static final int EVAL_INTERVAL = 4; // evaluate every 4th candle (1 hour equivalent)
    private static final int FUNDING_INTERVAL = 32; // every 32 candles = 8 hours of 15m

    /**
     * Run backtest simulation.
     */
    public MultiTimeFrameBacktestResult run(List<MultiTimeFrameStrategy> strategies,
                                             List<Candle> candles15m,
                                             BacktestConfig config) {
        // Initialize state
        double balance = config.getInitialCapital();
        double peakEquity = balance;
        double totalFees = 0;
        double totalSlippage = 0;
        double totalFundingPaid = 0;
        double grossProfit = 0;
        double grossLoss = 0;
        double dailyRealizedLoss = 0;

        List<Double> equityCurve = new ArrayList<>();
        List<Map<String, Object>> tradeLog = new ArrayList<>();
        Map<String, OpenPosition> openPositions = new HashMap<>(); // key: strategyName + "|" + symbol
        Map<String, int[]> strategyStats = new HashMap<>(); // strategyName -> [wins, total]

        // Initialize strategy stats
        for (MultiTimeFrameStrategy s : strategies) {
            strategyStats.put(s.getName(), new int[]{0, 0});
        }

        // Track exposure for risk engine
        Set<String> openPositionSymbols = new HashSet<>();
        double currentExposure = 0;

        equityCurve.add(balance);

        // Step through candles
        for (int i = 0; i < candles15m.size(); i++) {
            Candle currentCandle = candles15m.get(i);

            // Check open positions for stop/TP hits on every candle
            List<String> positionsToClose = new ArrayList<>();
            for (Map.Entry<String, OpenPosition> entry : openPositions.entrySet()) {
                OpenPosition pos = entry.getValue();
                Double exitPrice = checkPositionExit(pos, currentCandle);
                if (exitPrice != null) {
                    // Apply slippage on exit
                    double slippageAmount = exitPrice * config.getSlippageBps() / 10000.0;
                    double slippedExit = pos.isLong
                        ? exitPrice - slippageAmount  // slippage hurts on exit for longs
                        : exitPrice + slippageAmount; // slippage hurts on exit for shorts
                    totalSlippage += Math.abs(slippageAmount * pos.positionSize);

                    // Calculate P&L
                    double directionMultiplier = pos.isLong ? 1.0 : -1.0;
                    double rawPnl = (slippedExit - pos.entryPrice) * pos.positionSize * directionMultiplier;

                    // Apply exit fee
                    double feePct = config.isUseMakerOrders() ? config.getMakerFeePct() : config.getTakerFeePct();
                    double exitNotional = slippedExit * pos.positionSize;
                    double exitFee = exitNotional * feePct;
                    totalFees += exitFee;

                    double netPnl = rawPnl - exitFee;
                    balance += netPnl;

                    if (netPnl > 0) {
                        grossProfit += netPnl;
                        strategyStats.get(pos.strategyName)[0]++; // win
                    } else {
                        grossLoss += Math.abs(netPnl);
                        if (netPnl < 0) {
                            dailyRealizedLoss += Math.abs(netPnl);
                        }
                    }
                    strategyStats.get(pos.strategyName)[1]++; // total

                    // Log trade
                    Map<String, Object> trade = new LinkedHashMap<>();
                    trade.put("strategy", pos.strategyName);
                    trade.put("symbol", pos.symbol);
                    trade.put("direction", pos.isLong ? "LONG" : "SHORT");
                    trade.put("entryPrice", pos.entryPrice);
                    trade.put("exitPrice", slippedExit);
                    trade.put("positionSize", pos.positionSize);
                    trade.put("pnl", netPnl);
                    trade.put("fees", pos.entryFee + exitFee);
                    tradeLog.add(trade);

                    // Track exposure removal
                    currentExposure -= pos.entryPrice * pos.positionSize;
                    currentExposure = Math.max(0, currentExposure);
                    positionsToClose.add(entry.getKey());
                }
            }

            // Remove closed positions
            for (String key : positionsToClose) {
                OpenPosition closed = openPositions.remove(key);
                if (closed != null) {
                    // Rebuild open position symbols from remaining positions
                    openPositionSymbols.clear();
                    for (OpenPosition p : openPositions.values()) {
                        openPositionSymbols.add(p.symbol);
                    }
                }
            }

            // Apply funding rate costs every FUNDING_INTERVAL candles (8 hours)
            if (i > 0 && i % FUNDING_INTERVAL == 0 && !openPositions.isEmpty()) {
                for (OpenPosition pos : openPositions.values()) {
                    double notional = pos.entryPrice * pos.positionSize;
                    double fundingCost = notional * config.getFundingRatePer8h();
                    balance -= fundingCost;
                    totalFundingPaid += fundingCost;
                }
            }

            // Update peak equity
            if (balance > peakEquity) {
                peakEquity = balance;
            }

            // Stop if bankrupt
            if (balance <= 0) {
                log.warn("Backtest stopped: balance hit zero at candle {}", i);
                balance = 0;
                equityCurve.add(balance);
                break;
            }

            // Only evaluate strategies every EVAL_INTERVAL candles and after minimum warmup
            if (i < MIN_CANDLES_BEFORE_EVAL || i % EVAL_INTERVAL != 0) {
                // Still record equity periodically (every eval interval)
                if (i % EVAL_INTERVAL == 0) {
                    equityCurve.add(balance);
                }
                continue;
            }

            // Build window of last MAX_WINDOW_SIZE 15m candles (NO look-ahead)
            int windowStart = Math.max(0, i + 1 - MAX_WINDOW_SIZE);
            List<Candle> window15m = candles15m.subList(windowStart, i + 1);

            // Aggregate to higher timeframes
            List<Candle> candles1h = candleAggregator.aggregate(window15m, TimeFrame.M15, TimeFrame.H1);
            List<Candle> candles4h = candleAggregator.aggregate(window15m, TimeFrame.M15, TimeFrame.H4);

            // Calculate indicators for each timeframe
            IndicatorSnapshot ind15m = indicatorCalculator.calculate(new ArrayList<>(window15m), TimeFrame.M15);
            IndicatorSnapshot ind1h = indicatorCalculator.calculate(candles1h, TimeFrame.H1);
            IndicatorSnapshot ind4h = indicatorCalculator.calculate(candles4h, TimeFrame.H4);

            // Skip if we don't have enough data for indicators
            if (ind15m == null) {
                equityCurve.add(balance);
                continue;
            }

            // Build MultiTimeFrameData
            MultiTimeFrameData data = MultiTimeFrameData.builder()
                .symbol("BTCUSD")
                .currentPrice(currentCandle.close())
                .currentVolume(currentCandle.volume())
                .candles15m(new ArrayList<>(window15m))
                .candles1h(candles1h)
                .candles4h(candles4h)
                .indicators15m(ind15m)
                .indicators1h(ind1h)
                .indicators4h(ind4h)
                .fundingRate(config.getFundingRatePer8h())
                .fundingRateHistory(List.of())
                .build();

            // Run each strategy
            for (MultiTimeFrameStrategy strategy : strategies) {
                String posKey = strategy.getName() + "|" + "BTCUSD";

                // Skip if already have position for this strategy+symbol
                if (openPositions.containsKey(posKey)) {
                    continue;
                }

                try {
                    Optional<TradeSignal> signalOpt = strategy.analyze(data);
                    if (signalOpt.isEmpty()) {
                        continue;
                    }

                    TradeSignal signal = signalOpt.get();
                    if (signal.getAction() == Action.HOLD) {
                        continue;
                    }

                    // Convert to TradeRequest
                    TradeRequest request = signal.toTradeRequest();

                    // Run through risk engine
                    RiskCheckResult riskResult = tradeRiskEngine.evaluate(
                        request, balance, peakEquity, currentExposure,
                        dailyRealizedLoss, openPositionSymbols, config.getRiskParameters()
                    );

                    if (!riskResult.isApproved()) {
                        log.debug("Trade rejected for {}: {}", signal.getSymbol(), riskResult.getRejectionReasons());
                        continue;
                    }

                    // Open position with slippage on entry
                    double entryPrice = signal.getEntryPrice();
                    boolean isLong = signal.getAction() == Action.BUY;
                    double slippageAmount = entryPrice * config.getSlippageBps() / 10000.0;
                    double slippedEntry = isLong
                        ? entryPrice + slippageAmount  // slippage hurts on entry for longs
                        : entryPrice - slippageAmount; // slippage hurts on entry for shorts
                    totalSlippage += Math.abs(slippageAmount * riskResult.getPositionSize());

                    // Apply entry fee
                    double feePct = config.isUseMakerOrders() ? config.getMakerFeePct() : config.getTakerFeePct();
                    double entryNotional = slippedEntry * riskResult.getPositionSize();
                    double entryFee = entryNotional * feePct;
                    totalFees += entryFee;
                    balance -= entryFee;

                    // Track position
                    OpenPosition pos = new OpenPosition(
                        signal.getSymbol(), strategy.getName(), slippedEntry,
                        signal.getStopLossPrice(), signal.getTakeProfitPrice(),
                        riskResult.getPositionSize(), isLong, entryFee
                    );
                    openPositions.put(posKey, pos);
                    openPositionSymbols.add(signal.getSymbol());
                    currentExposure += entryNotional;

                    log.debug("Opened {} position for {} via {}: entry={}, size={}, stop={}, tp={}",
                        isLong ? "LONG" : "SHORT", signal.getSymbol(), strategy.getName(),
                        slippedEntry, riskResult.getPositionSize(),
                        signal.getStopLossPrice(), signal.getTakeProfitPrice());

                } catch (Exception e) {
                    log.error("Strategy {} threw exception: {}", strategy.getName(), e.getMessage());
                }
            }

            equityCurve.add(balance);
        }

        // Count trades
        int totalTrades = tradeLog.size();
        int winningTrades = 0;
        int losingTrades = 0;
        for (Map<String, Object> trade : tradeLog) {
            double pnl = (double) trade.get("pnl");
            if (pnl > 0) winningTrades++;
            else losingTrades++;
        }

        // Calculate metrics
        double totalReturnPct = (balance - config.getInitialCapital()) / config.getInitialCapital() * 100;
        double winRate = totalTrades > 0 ? (double) winningTrades / totalTrades * 100 : 0;
        double profitFactor = grossLoss > 0 ? grossProfit / grossLoss : 0;
        double maxDrawdownPct = calculateMaxDrawdown(equityCurve);
        double sharpeRatio = calculateSharpe(equityCurve);

        // Per-strategy win rate
        Map<String, Double> perStrategyWinRate = new HashMap<>();
        for (Map.Entry<String, int[]> entry : strategyStats.entrySet()) {
            int[] stats = entry.getValue();
            perStrategyWinRate.put(entry.getKey(), stats[1] > 0 ? (double) stats[0] / stats[1] * 100 : 0.0);
        }

        return MultiTimeFrameBacktestResult.builder()
            .initialCapital(config.getInitialCapital())
            .finalCapital(balance)
            .totalReturnPct(totalReturnPct)
            .sharpeRatio(sharpeRatio)
            .maxDrawdownPct(maxDrawdownPct)
            .winRate(winRate)
            .totalTrades(totalTrades)
            .winningTrades(winningTrades)
            .losingTrades(losingTrades)
            .profitFactor(profitFactor)
            .totalFees(totalFees)
            .totalSlippage(totalSlippage)
            .totalFundingPaid(totalFundingPaid)
            .equityCurve(equityCurve)
            .tradeLog(tradeLog)
            .perStrategyWinRate(perStrategyWinRate)
            .build();
    }

    /**
     * Check if the current candle triggers a stop-loss or take-profit exit.
     * Returns the exit price, or null if no exit.
     */
    private Double checkPositionExit(OpenPosition pos, Candle candle) {
        if (pos.isLong) {
            // For longs: check if low hit stop, high hit TP
            if (candle.low() <= pos.stopLoss) {
                return pos.stopLoss; // stopped out
            }
            if (candle.high() >= pos.takeProfit) {
                return pos.takeProfit; // TP hit
            }
        } else {
            // For shorts: check if high hit stop, low hit TP
            if (candle.high() >= pos.stopLoss) {
                return pos.stopLoss; // stopped out
            }
            if (candle.low() <= pos.takeProfit) {
                return pos.takeProfit; // TP hit
            }
        }
        return null;
    }

    /**
     * Calculate max drawdown from equity curve as a percentage.
     */
    private double calculateMaxDrawdown(List<Double> equityCurve) {
        if (equityCurve.size() < 2) return 0;
        double peak = equityCurve.get(0);
        double maxDrawdown = 0;
        for (double equity : equityCurve) {
            if (equity > peak) {
                peak = equity;
            }
            double drawdown = (peak - equity) / peak;
            maxDrawdown = Math.max(maxDrawdown, drawdown);
        }
        return maxDrawdown * 100; // as percentage
    }

    /**
     * Calculate annualized Sharpe ratio from equity curve.
     * Uses step returns (each equity point is ~1 hour apart).
     */
    private double calculateSharpe(List<Double> equityCurve) {
        if (equityCurve.size() < 3) return 0;

        // Calculate returns between equity curve points
        List<Double> returns = new ArrayList<>();
        for (int i = 1; i < equityCurve.size(); i++) {
            if (equityCurve.get(i - 1) > 0) {
                returns.add((equityCurve.get(i) - equityCurve.get(i - 1)) / equityCurve.get(i - 1));
            }
        }

        if (returns.isEmpty()) return 0;

        // Mean return
        double meanReturn = returns.stream().mapToDouble(Double::doubleValue).average().orElse(0);

        // Standard deviation
        double variance = returns.stream()
            .mapToDouble(r -> (r - meanReturn) * (r - meanReturn))
            .average().orElse(0);
        double stdDev = Math.sqrt(variance);

        if (stdDev == 0) return 0;

        // Annualize: each point is ~1 hour apart, so ~24 points per day, 252 trading days
        // For crypto (24/7): ~8760 hours per year, but we use 252*24 = 6048 for comparison
        double annualizationFactor = Math.sqrt(6048);
        return (meanReturn / stdDev) * annualizationFactor;
    }

    /**
     * Internal class to track an open position.
     */
    private static class OpenPosition {
        final String symbol;
        final String strategyName;
        final double entryPrice;
        final double stopLoss;
        final double takeProfit;
        final double positionSize;
        final boolean isLong;
        final double entryFee;

        OpenPosition(String symbol, String strategyName, double entryPrice,
                     double stopLoss, double takeProfit, double positionSize,
                     boolean isLong, double entryFee) {
            this.symbol = symbol;
            this.strategyName = strategyName;
            this.entryPrice = entryPrice;
            this.stopLoss = stopLoss;
            this.takeProfit = takeProfit;
            this.positionSize = positionSize;
            this.isLong = isLong;
            this.entryFee = entryFee;
        }
    }
}
