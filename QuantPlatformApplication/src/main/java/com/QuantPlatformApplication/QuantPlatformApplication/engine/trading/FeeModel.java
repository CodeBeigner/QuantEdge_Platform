package com.QuantPlatformApplication.QuantPlatformApplication.engine.trading;

/**
 * Shared fee + funding arithmetic.
 *
 * - Fees are expressed as fractions (0.0003 = 3 bps = 0.03%).
 * - Funding rate is expressed per 8-hour interval.
 * - Both engines call these helpers so their cost models stay in sync.
 */
public final class FeeModel {

    private FeeModel() {}

    /** Fee charged on entry for a notional position. */
    public static double entryFee(double notional, double makerPct, double takerPct, boolean useMaker) {
        double pct = useMaker ? makerPct : takerPct;
        return notional * pct;
    }

    /** Fee charged on exit for a notional position (symmetrical by default). */
    public static double exitFee(double notional, double makerPct, double takerPct, boolean useMaker) {
        return entryFee(notional, makerPct, takerPct, useMaker);
    }

    /** Total entry + exit fee for a round-trip. */
    public static double roundTripCost(double notional, double makerPct, double takerPct, boolean useMaker) {
        return entryFee(notional, makerPct, takerPct, useMaker) + exitFee(notional, makerPct, takerPct, useMaker);
    }

    /**
     * Funding cost for holding a notional position through `intervalsElapsed`
     * 8-hour funding windows. Sign convention: positive value = cost TO the trader.
     * The caller is responsible for sign logic when longs receive and shorts pay
     * (or vice versa) based on realized funding rate direction.
     */
    public static double fundingCost(double notional, double fundingRatePer8h, int intervalsElapsed) {
        if (intervalsElapsed <= 0) return 0.0;
        return notional * fundingRatePer8h * intervalsElapsed;
    }
}
