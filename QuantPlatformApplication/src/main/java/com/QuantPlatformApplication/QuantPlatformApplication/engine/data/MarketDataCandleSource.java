package com.QuantPlatformApplication.QuantPlatformApplication.engine.data;

import com.QuantPlatformApplication.QuantPlatformApplication.engine.model.Candle;
import com.QuantPlatformApplication.QuantPlatformApplication.engine.model.TimeFrame;
import com.QuantPlatformApplication.QuantPlatformApplication.model.entity.MarketDataEntity;
import com.QuantPlatformApplication.QuantPlatformApplication.service.MarketDataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Candle source backed by Postgres `market_data`. Reads via MarketDataService
 * and converts MarketDataEntity rows into the engine's Candle record.
 *
 * If the requested window has no rows, raises EmptyCandleRangeException so the
 * controller can return HTTP 503 ("seed your data"). Silent fallback to
 * synthetic candles is NOT supported here — callers that want REST fallback
 * must compose a different CandleSource.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MarketDataCandleSource implements CandleSource {

    private final MarketDataService marketDataService;

    @Override
    public List<Candle> fetch(String symbol, String timeframe, LocalDate startDate, LocalDate endDate) {
        Instant start = startDate.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant end = endDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();

        List<MarketDataEntity> rows = marketDataService.fetchDailyData(symbol.toUpperCase(), timeframe, start, end);
        if (rows.isEmpty()) {
            throw new EmptyCandleRangeException(
                "No candles for " + symbol + " " + timeframe + " in " + startDate + ".." + endDate
                + ". Seed data with `python -m ingest.seed_binance_vision --symbols " + symbol + "` first."
            );
        }

        log.info("MarketDataCandleSource: fetched {} rows for {} {} {}..{}",
                rows.size(), symbol, timeframe, startDate, endDate);

        return rows.stream()
                .map(e -> toCandle(e, timeframe))
                .toList();
    }

    private Candle toCandle(MarketDataEntity e, String timeframeStr) {
        TimeFrame timeFrame = parseTimeFrame(timeframeStr);
        return new Candle(
                e.getTime(),
                e.getOpen().doubleValue(),
                e.getHigh().doubleValue(),
                e.getLow().doubleValue(),
                e.getClose().doubleValue(),
                e.getVolume() != null ? e.getVolume().doubleValue() : 0.0,
                timeFrame
        );
    }

    private TimeFrame parseTimeFrame(String timeframe) {
        return switch (timeframe.toLowerCase()) {
            case "15m" -> TimeFrame.M15;
            case "1h" -> TimeFrame.H1;
            case "4h" -> TimeFrame.H4;
            default -> throw new IllegalArgumentException("Unsupported timeframe: " + timeframe);
        };
    }
}
