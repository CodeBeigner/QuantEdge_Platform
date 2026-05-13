package com.QuantPlatformApplication.QuantPlatformApplication.engine.trading;

/**
 * Shared slippage arithmetic used by both backtest engines and the paper
 * broker. Slippage is expressed in basis points (1 bp = 0.01%).
 *
 * All methods are pure — no state, no randomness at runtime. The
 * deterministicForOrderId variant uses a stable hash of the order id so
 * replaying the same order sequence yields identical fills.
 */
public final class SlippageModel {

    private SlippageModel() {}

    /** Apply `bps` of slippage against a BUY fill (price goes up). */
    public static double applyToBuy(double price, double bps) {
        return price * (1.0 + bps / 10_000.0);
    }

    /** Apply `bps` of slippage against a SELL fill (price goes down). */
    public static double applyToSell(double price, double bps) {
        return price * (1.0 - bps / 10_000.0);
    }

    /**
     * Return deterministic bps in [minBps, maxBps] keyed on orderId.
     * Useful for the paper broker so fills are reproducible across reruns.
     */
    public static double deterministicForOrderId(String orderId, double minBps, double maxBps) {
        if (maxBps <= minBps) return minBps;
        int hash = orderId == null ? 0 : orderId.hashCode();
        double frac = (Math.abs(hash) % 10_000) / 10_000.0; // [0, 1)
        return minBps + frac * (maxBps - minBps);
    }
}
