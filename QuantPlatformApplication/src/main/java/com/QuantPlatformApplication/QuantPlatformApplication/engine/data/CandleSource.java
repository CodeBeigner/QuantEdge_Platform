package com.QuantPlatformApplication.QuantPlatformApplication.engine.data;

import com.QuantPlatformApplication.QuantPlatformApplication.engine.model.Candle;

import java.time.LocalDate;
import java.util.List;

/**
 * Pluggable source of historical OHLCV candles. Implementations read from
 * Postgres, Binance REST, files, etc. The backtest engines are written
 * against this interface so the data source is swappable.
 */
public interface CandleSource {
    /**
     * Fetch candles for (symbol, timeframe) between startDate and endDate inclusive.
     * Returns candles in chronological ascending order.
     *
     * @throws EmptyCandleRangeException when the requested window yields zero candles
     *         and no fallback path is configured. The caller is expected to translate
     *         this to a loud error (HTTP 503) rather than returning empty silently.
     */
    List<Candle> fetch(String symbol, String timeframe, LocalDate startDate, LocalDate endDate);
}
