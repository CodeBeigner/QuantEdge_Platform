package com.QuantPlatformApplication.QuantPlatformApplication.service.pipeline;

import com.QuantPlatformApplication.QuantPlatformApplication.engine.data.CandleSource;
import com.QuantPlatformApplication.QuantPlatformApplication.engine.model.Candle;
import com.QuantPlatformApplication.QuantPlatformApplication.engine.model.IndicatorSnapshot;
import com.QuantPlatformApplication.QuantPlatformApplication.engine.model.MultiTimeFrameData;
import com.QuantPlatformApplication.QuantPlatformApplication.engine.model.TimeFrame;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

/**
 * Assembles a MultiTimeFrameData snapshot from Postgres for live paper trading.
 *
 * Uses the same CandleSource the backtest uses (MarketDataCandleSource), so the
 * paper-trading data path is byte-for-byte identical to the backtest data path —
 * no "it worked in backtest but not live" class of bug.
 *
 * The builder pulls the last N calendar days of 15m/1h/4h candles anchored to
 * `asOf`, then asks IndicatorCalculator to compute snapshots. If any timeframe
 * is thin (e.g., just after a gap), calculate() returns null and the strategy
 * downstream skips evaluation for that tick.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MultiTimeFrameDataBuilder {

    // Enough history for the longest indicator (50-period EMA on 4h = 200h = 8 days),
    // plus margin for warm-up and weekend gaps.
    private static final int LOOKBACK_DAYS_15M = 14;
    private static final int LOOKBACK_DAYS_1H  = 30;
    private static final int LOOKBACK_DAYS_4H  = 90;

    private final CandleSource candleSource;
    private final IndicatorCalculator indicatorCalculator;

    public MultiTimeFrameData build(String symbol, LocalDate asOf) {
        List<Candle> c15 = safeFetch(symbol, "15m", asOf.minusDays(LOOKBACK_DAYS_15M), asOf);
        List<Candle> c1h = safeFetch(symbol, "1h",  asOf.minusDays(LOOKBACK_DAYS_1H),  asOf);
        List<Candle> c4h = safeFetch(symbol, "4h",  asOf.minusDays(LOOKBACK_DAYS_4H),  asOf);

        IndicatorSnapshot i15 = c15.isEmpty() ? null : indicatorCalculator.calculate(c15, TimeFrame.M15);
        IndicatorSnapshot i1h = c1h.isEmpty() ? null : indicatorCalculator.calculate(c1h, TimeFrame.H1);
        IndicatorSnapshot i4h = c4h.isEmpty() ? null : indicatorCalculator.calculate(c4h, TimeFrame.H4);

        Candle last15 = c15.isEmpty() ? null : c15.get(c15.size() - 1);
        double currentPrice = last15 != null ? last15.close() : 0.0;
        double currentVolume = last15 != null ? last15.volume() : 0.0;

        return MultiTimeFrameData.builder()
            .symbol(symbol)
            .currentPrice(currentPrice)
            .currentVolume(currentVolume)
            .candles15m(c15)
            .candles1h(c1h)
            .candles4h(c4h)
            .indicators15m(i15)
            .indicators1h(i1h)
            .indicators4h(i4h)
            .fundingRate(0.0)               // Plan 4.1: funding enrichment is a follow-up
            .fundingRatePredicted(0.0)
            .fundingRateHistory(Collections.emptyList())
            .openInterest(0.0)
            .openInterestChange24h(0.0)
            .longShortRatio(0.0)
            .build();
    }

    private List<Candle> safeFetch(String symbol, String tf, LocalDate from, LocalDate to) {
        try {
            return candleSource.fetch(symbol, tf, from, to);
        } catch (Exception e) {
            log.warn("CandleSource fetch failed for {} {} {}..{}: {}", symbol, tf, from, to, e.getMessage());
            return Collections.emptyList();
        }
    }
}
