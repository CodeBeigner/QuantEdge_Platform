package com.QuantPlatformApplication.QuantPlatformApplication.engine.data;

/**
 * Thrown by CandleSource implementations when the requested (symbol, timeframe,
 * start..end) window has no rows in the underlying store and REST fallback is
 * disabled. Controllers translate this to HTTP 503 with a clear remediation
 * message ("seed the data first").
 */
public class EmptyCandleRangeException extends RuntimeException {
    public EmptyCandleRangeException(String message) {
        super(message);
    }
}
