package com.QuantPlatformApplication.QuantPlatformApplication.engine.strategy;

import com.QuantPlatformApplication.QuantPlatformApplication.engine.model.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Funding Sentiment Strategy — Crypto-Native Crowd Positioning.
 *
 * Exploits overleveraged crowd positioning via funding rate and open interest analysis.
 * When the crowd is heavily positioned one direction, a reversal creates liquidation cascades.
 *
 * Funding Rate Analysis:
 *   - Funding > 0.05%/8h for 3+ consecutive periods -> crowd heavily LONG -> look for SHORT
 *   - Funding < -0.03%/8h for 3+ consecutive periods -> crowd heavily SHORT -> look for LONG
 *   - Neutral funding -> no trade
 *
 * OI Confirmation:
 *   - Rising OI (>10% change in 24h) + extreme funding = new positions entering -> maximum conviction
 *
 * Price Confirmation (REQUIRED — don't fade blindly):
 *   - 1H: EMA slope opposing the crowd (bearish slope when crowd is long)
 *   - 15M: momentum candle with volume > 1.3x average confirming the reversal
 *   - Without price confirmation, skip or reduce confidence significantly
 *
 * Take-profit: Aggressive 3:1 to 5:1 R:R (liquidation cascades move fast and far)
 * Stop: Tight — 1.5x ATR from entry
 * Trail: Aggressively once 2R reached — noted in metadata
 */
@Slf4j
@Component
public class FundingSentimentStrategy implements MultiTimeFrameStrategy {

    // Funding rate thresholds (per 8h period)
    private static final double FUNDING_EXTREME_POSITIVE = 0.05;  // 0.05% = crowd heavily long
    private static final double FUNDING_EXTREME_NEGATIVE = -0.03; // -0.03% = crowd heavily short
    private static final int CONSECUTIVE_PERIODS_REQUIRED = 3;

    // OI thresholds
    private static final double OI_SPIKE_THRESHOLD = 10.0; // 10% change in 24h

    // Price confirmation
    private static final double VOLUME_CONFIRMATION_RATIO = 1.3;

    // Risk/reward
    private static final double STOP_ATR_MULTIPLIER = 1.5;
    private static final double TP_RR_RATIO = 4.0; // Target 4:1 R:R (midpoint of 3:1 to 5:1)
    private static final double TRAIL_START_RR = 2.0;

    // Confidence modifiers
    private static final double BASE_CONFIDENCE = 0.55;
    private static final double OI_SPIKE_BOOST = 0.15;
    private static final double PRICE_CONFIRMATION_BOOST = 0.1;
    private static final double NO_PRICE_CONFIRMATION_PENALTY = 0.15;

    @Override
    public ModelType getModelType() {
        return ModelType.FUNDING_SENTIMENT;
    }

    @Override
    public String getName() {
        return "Funding Sentiment";
    }

    @Override
    public Optional<TradeSignal> analyze(MultiTimeFrameData data) {
        if (!data.isComplete()) {
            return Optional.empty();
        }

        List<Double> fundingHistory = data.getFundingRateHistory();
        if (fundingHistory == null || fundingHistory.isEmpty()) {
            return Optional.empty();
        }

        IndicatorSnapshot h4 = data.indicators(TimeFrame.H4);
        IndicatorSnapshot h1 = data.indicators(TimeFrame.H1);
        IndicatorSnapshot m15 = data.indicators(TimeFrame.M15);

        // Step 1: Check for extreme funding over consecutive periods
        boolean crowdLong = isExtremeFunding(fundingHistory, FUNDING_EXTREME_POSITIVE, CONSECUTIVE_PERIODS_REQUIRED);
        boolean crowdShort = isExtremeFundingNegative(fundingHistory, FUNDING_EXTREME_NEGATIVE, CONSECUTIVE_PERIODS_REQUIRED);

        if (!crowdLong && !crowdShort) {
            return Optional.empty();
        }

        // When crowd is long -> we look for SHORT; when crowd is short -> we look for LONG
        Action action = crowdLong ? Action.SELL : Action.BUY;

        // Step 2: Check OI for conviction amplification
        boolean oiSpike = hasOiSpike(data.getOpenInterestChange24h());

        // Step 3: Price confirmation — REQUIRED, don't fade blindly
        boolean hasPriceConf = hasPriceConfirmation(h1, m15, action);

        // Without any price confirmation, skip the trade entirely
        if (!hasPriceConf && m15.volumeRatio() < 1.0) {
            return Optional.empty();
        }

        // Step 4: Calculate entry, stop, and take-profit
        double entry = data.getCurrentPrice();
        double atr = m15.atr14();
        double stopLoss;
        double takeProfit;

        if (action == Action.SELL) {
            stopLoss = entry + atr * STOP_ATR_MULTIPLIER;
            double riskDistance = stopLoss - entry;
            takeProfit = entry - riskDistance * TP_RR_RATIO;
        } else {
            stopLoss = entry - atr * STOP_ATR_MULTIPLIER;
            double riskDistance = entry - stopLoss;
            takeProfit = entry + riskDistance * TP_RR_RATIO;
        }

        // Step 5: Calculate confidence
        double confidence = BASE_CONFIDENCE;

        if (oiSpike) {
            confidence += OI_SPIKE_BOOST;
        }

        if (hasPriceConf) {
            confidence += PRICE_CONFIRMATION_BOOST;
        } else {
            confidence -= NO_PRICE_CONFIRMATION_PENALTY;
        }

        confidence = Math.max(0.1, Math.min(1.0, confidence));

        // Step 6: Build explanations
        String fundingExplanation = buildFundingExplanation(data.getFundingRate(), fundingHistory, crowdLong);
        String biasExplanation = buildBiasExplanation(crowdLong, fundingHistory);
        String zoneExplanation = buildOiExplanation(data.getOpenInterestChange24h(), oiSpike);
        String triggerExplanation = buildTriggerExplanation(h1, m15, action, hasPriceConf);
        String riskExplanation = String.format(
            "Entry: %.2f | Stop: %.2f (%.1fx ATR) | TP: %.2f | R:R = %.1f:1 | Trail at %.1fR",
            entry, stopLoss, STOP_ATR_MULTIPLIER, takeProfit, TP_RR_RATIO, TRAIL_START_RR);

        String lesson = crowdLong
            ? "Funding sentiment: when funding rates stay extreme positive (>0.05%/8h) for multiple periods, "
              + "the market is overcrowded with longs paying high rates to maintain positions. "
              + "This creates fuel for a liquidation cascade — when price dips, overleveraged longs get stopped out, "
              + "pushing price further down in a self-reinforcing loop."
            : "Funding sentiment: extreme negative funding means shorts are paying to maintain positions. "
              + "When this persists for multiple periods, short positions become crowded and vulnerable. "
              + "A price bounce triggers short liquidations, creating a squeeze that amplifies the move upward.";

        TradeSignal signal = TradeSignal.builder()
            .symbol(data.getSymbol())
            .action(action)
            .entryPrice(entry)
            .stopLossPrice(stopLoss)
            .takeProfitPrice(takeProfit)
            .confidence(confidence)
            .strategyName(getName())
            .biasExplanation(biasExplanation)
            .zoneExplanation(zoneExplanation)
            .triggerExplanation(triggerExplanation)
            .fundingExplanation(fundingExplanation)
            .riskExplanation(riskExplanation)
            .lesson(lesson)
            .metadata(Map.of(
                "funding_rate", data.getFundingRate(),
                "funding_history_size", fundingHistory.size(),
                "crowd_direction", crowdLong ? "LONG" : "SHORT",
                "oi_change_24h", data.getOpenInterestChange24h(),
                "oi_spike", oiSpike,
                "price_confirmed", hasPriceConf,
                "trail_start_rr", TRAIL_START_RR,
                "tp_rr_ratio", TP_RR_RATIO
            ))
            .build();

        log.info("FUNDING_SENTIMENT signal: {} {} @ {} (conf: {}, crowd: {}, OI spike: {})",
            signal.getAction(), data.getSymbol(), entry, confidence,
            crowdLong ? "LONG" : "SHORT", oiSpike);

        return Optional.of(signal);
    }

    /**
     * Check if N consecutive funding rates are above the positive threshold.
     */
    boolean isExtremeFunding(List<Double> fundingRateHistory, double threshold, int consecutivePeriods) {
        if (fundingRateHistory.size() < consecutivePeriods) {
            return false;
        }
        // Check the most recent N periods
        int start = fundingRateHistory.size() - consecutivePeriods;
        for (int i = start; i < fundingRateHistory.size(); i++) {
            if (fundingRateHistory.get(i) <= threshold) {
                return false;
            }
        }
        return true;
    }

    /**
     * Check if N consecutive funding rates are below the negative threshold.
     */
    boolean isExtremeFundingNegative(List<Double> fundingRateHistory, double threshold, int consecutivePeriods) {
        if (fundingRateHistory.size() < consecutivePeriods) {
            return false;
        }
        int start = fundingRateHistory.size() - consecutivePeriods;
        for (int i = start; i < fundingRateHistory.size(); i++) {
            if (fundingRateHistory.get(i) >= threshold) {
                return false;
            }
        }
        return true;
    }

    /**
     * Check if OI changed more than the spike threshold in 24h.
     */
    boolean hasOiSpike(double openInterestChange24h) {
        return Math.abs(openInterestChange24h) > OI_SPIKE_THRESHOLD;
    }

    /**
     * Check price confirmation: 1H EMA slope opposing the crowd + 15M volume confirmation.
     */
    boolean hasPriceConfirmation(IndicatorSnapshot h1, IndicatorSnapshot m15, Action direction) {
        boolean h1SlopeConfirms;
        if (direction == Action.SELL) {
            // For short: 1H EMA slope should be bearish (negative) — opposing the long crowd
            h1SlopeConfirms = h1.ema21Slope() < 0;
        } else {
            // For long: 1H EMA slope should be bullish (positive) — opposing the short crowd
            h1SlopeConfirms = h1.ema21Slope() > 0;
        }

        boolean m15VolumeConfirms = m15.volumeRatio() > VOLUME_CONFIRMATION_RATIO;

        return h1SlopeConfirms && m15VolumeConfirms;
    }

    private String buildFundingExplanation(double currentRate, List<Double> history, boolean crowdLong) {
        int consecutiveCount = 0;
        if (crowdLong) {
            for (int i = history.size() - 1; i >= 0; i--) {
                if (history.get(i) > FUNDING_EXTREME_POSITIVE) {
                    consecutiveCount++;
                } else {
                    break;
                }
            }
        } else {
            for (int i = history.size() - 1; i >= 0; i--) {
                if (history.get(i) < FUNDING_EXTREME_NEGATIVE) {
                    consecutiveCount++;
                } else {
                    break;
                }
            }
        }

        return String.format(
            "Funding rate: %.4f (%s extreme for %d consecutive periods). %s "
                + "This persistent extreme indicates overleveraged crowd positioning.",
            currentRate,
            crowdLong ? "positive" : "negative",
            consecutiveCount,
            crowdLong
                ? String.format("Longs paying %.2f%% per 8h.", currentRate * 100)
                : String.format("Shorts paying %.2f%% per 8h.", Math.abs(currentRate) * 100));
    }

    private String buildBiasExplanation(boolean crowdLong, List<Double> history) {
        double avgFunding = history.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        return String.format(
            "Crowd is heavily %s (avg funding: %.4f over %d periods). "
                + "Looking for %s reversal as liquidation cascade potential is high.",
            crowdLong ? "LONG" : "SHORT",
            avgFunding,
            history.size(),
            crowdLong ? "SHORT" : "LONG");
    }

    private String buildOiExplanation(double oiChange, boolean oiSpike) {
        if (oiSpike) {
            return String.format(
                "Open Interest changed %.1f%% in 24h — new positions entering aggressively. "
                    + "Combined with extreme funding, this signals maximum crowd conviction (and vulnerability).",
                oiChange);
        }
        return String.format(
            "Open Interest changed %.1f%% in 24h — moderate positioning. "
                + "No major OI spike, but funding extreme still provides edge.",
            oiChange);
    }

    private String buildTriggerExplanation(IndicatorSnapshot h1, IndicatorSnapshot m15,
                                            Action action, boolean priceConfirmed) {
        if (priceConfirmed) {
            return String.format(
                "Price confirmation: 1H EMA slope %.2f (%s crowd), "
                    + "15M volume %.1fx average (>%.1fx threshold). Reversal momentum confirmed.",
                h1.ema21Slope(),
                action == Action.SELL ? "opposing long" : "opposing short",
                m15.volumeRatio(),
                VOLUME_CONFIRMATION_RATIO);
        }
        return String.format(
            "Limited price confirmation: 1H EMA slope %.2f, 15M volume %.1fx average. "
                + "Entering with reduced confidence — monitor closely for reversal signs.",
            h1.ema21Slope(),
            m15.volumeRatio());
    }
}
