package com.QuantPlatformApplication.QuantPlatformApplication.service;

import com.QuantPlatformApplication.QuantPlatformApplication.engine.MultiTimeFrameBacktestEngine;
import com.QuantPlatformApplication.QuantPlatformApplication.engine.model.*;
import com.QuantPlatformApplication.QuantPlatformApplication.engine.strategy.MultiTimeFrameStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class MultiTimeFrameBacktestService {

    private final MultiTimeFrameBacktestEngine backtestEngine;
    private final List<MultiTimeFrameStrategy> strategies;

    public MultiTimeFrameBacktestResult runBacktest(List<Candle> candles15m, BacktestConfig config) {
        log.info("Starting multi-TF backtest: {} candles, ${} capital",
            candles15m.size(), config.getInitialCapital());

        MultiTimeFrameBacktestResult result = backtestEngine.run(strategies, candles15m, config);

        log.info("Backtest complete: {}% return, {}% win rate, {}% max DD, {} trades",
            result.getTotalReturnPct(), result.getWinRate(),
            result.getMaxDrawdownPct(), result.getTotalTrades());

        return result;
    }
}
