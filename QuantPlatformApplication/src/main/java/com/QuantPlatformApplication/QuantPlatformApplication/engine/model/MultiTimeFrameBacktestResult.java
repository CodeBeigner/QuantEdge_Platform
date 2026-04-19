package com.QuantPlatformApplication.QuantPlatformApplication.engine.model;

import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Map;

@Getter
@Builder
public class MultiTimeFrameBacktestResult {
    private final double initialCapital;
    private final double finalCapital;
    private final double totalReturnPct;
    private final double sharpeRatio;
    private final double maxDrawdownPct;
    private final double winRate;
    private final int totalTrades;
    private final int winningTrades;
    private final int losingTrades;
    private final double profitFactor;
    private final double totalFees;
    private final double totalSlippage;
    private final double totalFundingPaid;
    private final List<Double> equityCurve;
    private final List<Map<String, Object>> tradeLog;   // Individual trade records
    private final Map<String, Double> perStrategyWinRate; // Strategy name -> win rate
}
