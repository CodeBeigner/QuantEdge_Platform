package com.QuantPlatformApplication.QuantPlatformApplication.service.pipeline;

import com.QuantPlatformApplication.QuantPlatformApplication.engine.model.*;
import com.QuantPlatformApplication.QuantPlatformApplication.service.StrategyOrchestrator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Orchestrates the full live data pipeline:
 * Delta Exchange WebSocket -> candle buffer -> aggregation -> indicators -> strategy evaluation
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MarketDataPipeline {

    private final DeltaExchangeDataFeed dataFeed;
    private final CandleAggregator aggregator;
    private final IndicatorCalculator indicatorCalculator;
    private final FundingRateTracker fundingRateTracker;
    private final AccountStateTracker accountState;
    private final StrategyOrchestrator orchestrator;

    private final List<Candle> candleBuffer15m = new ArrayList<>();
    private static final int MAX_BUFFER_SIZE = 500; // ~5 days of 15m candles
    private String activeSymbol;
    private RiskParameters riskParameters;
    private String executionMode = "HUMAN_IN_LOOP";

    public void start(String symbol, boolean testnet, RiskParameters params, String mode) {
        this.activeSymbol = symbol;
        this.riskParameters = params;
        this.executionMode = mode;

        log.info("Starting live pipeline for {} (testnet={}, mode={})", symbol, testnet, mode);

        dataFeed.connect(testnet, symbol,
            this::onNewCandle,
            fundingRateTracker::recordFundingRate
        );
    }

    public void stop() {
        dataFeed.disconnect();
        log.info("Live pipeline stopped for {}", activeSymbol);
    }

    public void setExecutionMode(String mode) {
        this.executionMode = mode;
        log.info("Execution mode changed to: {}", mode);
    }

    private void onNewCandle(Candle candle) {
        candleBuffer15m.add(candle);
        while (candleBuffer15m.size() > MAX_BUFFER_SIZE) {
            candleBuffer15m.remove(0);
        }

        if (candleBuffer15m.size() < 64) { // Need at least 64 candles for 4h aggregation + indicators
            log.debug("Buffering candles: {}/{}", candleBuffer15m.size(), 64);
            return;
        }

        // Aggregate to higher timeframes
        List<Candle> candles1h = aggregator.aggregate(candleBuffer15m, TimeFrame.M15, TimeFrame.H1);
        List<Candle> candles4h = aggregator.aggregate(candleBuffer15m, TimeFrame.M15, TimeFrame.H4);

        // Calculate indicators
        IndicatorSnapshot ind15m = indicatorCalculator.calculate(candleBuffer15m, TimeFrame.M15);
        IndicatorSnapshot ind1h = indicatorCalculator.calculate(candles1h, TimeFrame.H1);
        IndicatorSnapshot ind4h = indicatorCalculator.calculate(candles4h, TimeFrame.H4);

        // Build multi-timeframe data
        MultiTimeFrameData data = MultiTimeFrameData.builder()
            .symbol(activeSymbol)
            .currentPrice(candle.close())
            .currentVolume(candle.volume())
            .candles15m(new ArrayList<>(candleBuffer15m))
            .candles1h(candles1h)
            .candles4h(candles4h)
            .indicators15m(ind15m)
            .indicators1h(ind1h)
            .indicators4h(ind4h)
            .fundingRate(fundingRateTracker.getCurrentRate())
            .fundingRateHistory(fundingRateTracker.getHistory())
            .build();

        // Evaluate strategies
        orchestrator.evaluateStrategies(
            data,
            accountState.getCurrentBalance(),
            accountState.getPeakEquity(),
            accountState.getCurrentExposure(),
            accountState.getDailyRealizedLoss(),
            accountState.getOpenPositionSymbols(),
            riskParameters,
            executionMode
        );
    }
}
