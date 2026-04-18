package com.QuantPlatformApplication.QuantPlatformApplication.engine.strategy;

import com.QuantPlatformApplication.QuantPlatformApplication.engine.model.*;
import com.QuantPlatformApplication.QuantPlatformApplication.engine.util.SwingDetector;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Trend Continuation Strategy — Multi-Timeframe.
 *
 * 4H: Determines directional bias via EMA21 slope
 * 1H: Identifies pullback zones (swing lows for longs, swing highs for shorts)
 * 15M: Entry trigger — RSI bounce, volume spike, MACD confirmation
 *
 * Funding rate acts as a confidence modifier.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TrendContinuationStrategy implements MultiTimeFrameStrategy {

    private static final double EMA_SLOPE_THRESHOLD = 1.0;  // Minimum slope for bias
    private static final double RSI_PULLBACK_LONG = 45;      // RSI below this = pullback for longs
    private static final double RSI_PULLBACK_SHORT = 55;     // RSI above this = pullback for shorts
    private static final double VOLUME_CONFIRMATION = 1.3;    // 1.3x average volume
    private static final double TP1_RR = 1.5;
    private static final double TP2_RR = 2.5;
    private static final double FUNDING_CONFIDENCE_BOOST = 0.1;
    private static final double ZONE_PROXIMITY_PCT = 0.005;   // Within 0.5% of zone

    private final SwingDetector swingDetector;

    @Override
    public ModelType getModelType() {
        return ModelType.TREND_CONTINUATION;
    }

    @Override
    public String getName() {
        return "Trend Continuation";
    }

    @Override
    public Optional<TradeSignal> analyze(MultiTimeFrameData data) {
        if (!data.isComplete()) {
            return Optional.empty();
        }

        IndicatorSnapshot h4 = data.indicators(TimeFrame.H4);
        IndicatorSnapshot h1 = data.indicators(TimeFrame.H1);
        IndicatorSnapshot m15 = data.indicators(TimeFrame.M15);

        // Step 1: Determine 4H bias
        Action bias = determineBias(h4);
        if (bias == Action.HOLD) {
            return Optional.empty();
        }

        String biasExplanation = buildBiasExplanation(h4, bias);

        // Step 2: Check if price is pulling back to a 1H zone
        double zoneLevel = findPullbackZone(data, bias);
        if (zoneLevel <= 0) {
            return Optional.empty();
        }

        // Check proximity to zone
        double distanceToZone = Math.abs(data.getCurrentPrice() - zoneLevel) / data.getCurrentPrice();
        if (distanceToZone > ZONE_PROXIMITY_PCT) {
            return Optional.empty();
        }

        String zoneExplanation = String.format(
            "1H %s zone at %.2f (price is %.2f%% away). Price pulling back toward institutional level.",
            bias == Action.BUY ? "support" : "resistance", zoneLevel, distanceToZone * 100);

        // Step 3: Check 15M entry trigger
        if (!hasEntryTrigger(m15, bias)) {
            return Optional.empty();
        }

        String triggerExplanation = buildTriggerExplanation(m15, bias);

        // Step 4: Calculate stops and targets
        double entry = data.getCurrentPrice();
        double atr = m15.atr14();
        double stopLoss;
        double takeProfit;

        if (bias == Action.BUY) {
            stopLoss = Math.min(zoneLevel - atr * 0.5, entry - atr * 1.5);
            double riskDistance = entry - stopLoss;
            takeProfit = entry + riskDistance * TP1_RR;
        } else {
            stopLoss = Math.max(zoneLevel + atr * 0.5, entry + atr * 1.5);
            double riskDistance = stopLoss - entry;
            takeProfit = entry - riskDistance * TP1_RR;
        }

        // Step 5: Calculate confidence with funding modifier
        double baseConfidence = calculateBaseConfidence(h4, h1, m15);
        double fundingBoost = calculateFundingBoost(data.getFundingRate(), bias);
        double confidence = Math.min(1.0, baseConfidence + fundingBoost);

        String fundingExplanation = buildFundingExplanation(data.getFundingRate(), bias, fundingBoost);

        String lesson = bias == Action.BUY
            ? "Trend continuation: higher timeframe establishes direction, pullback to key level provides entry with defined risk. Trading with the trend improves win rate because you're aligned with the dominant order flow."
            : "Trend continuation (short): when the 4H trend is bearish, rallies into resistance are selling opportunities. The key is waiting for the 15M to confirm rejection — don't sell into strength without confirmation.";

        TradeSignal signal = TradeSignal.builder()
            .symbol(data.getSymbol())
            .action(bias)
            .entryPrice(entry)
            .stopLossPrice(stopLoss)
            .takeProfitPrice(takeProfit)
            .confidence(confidence)
            .strategyName(getName())
            .biasExplanation(biasExplanation)
            .zoneExplanation(zoneExplanation)
            .triggerExplanation(triggerExplanation)
            .fundingExplanation(fundingExplanation)
            .riskExplanation(String.format("Entry: %.2f | Stop: %.2f | TP: %.2f | R:R = %.1f:1",
                entry, stopLoss, takeProfit, TP1_RR))
            .lesson(lesson)
            .metadata(Map.of(
                "h4_ema_slope", h4.ema21Slope(),
                "m15_rsi", m15.rsi14(),
                "m15_volume_ratio", m15.volumeRatio(),
                "funding_rate", data.getFundingRate(),
                "zone_level", zoneLevel
            ))
            .build();

        log.info("TREND_CONTINUATION signal: {} {} @ {} (conf: {})",
            signal.getAction(), data.getSymbol(), entry, confidence);

        return Optional.of(signal);
    }

    private Action determineBias(IndicatorSnapshot h4) {
        if (h4.ema21Slope() > EMA_SLOPE_THRESHOLD && h4.ema21() > h4.ema50()) {
            return Action.BUY;
        }
        if (h4.ema21Slope() < -EMA_SLOPE_THRESHOLD && h4.ema21() < h4.ema50()) {
            return Action.SELL;
        }
        return Action.HOLD;
    }

    private double findPullbackZone(MultiTimeFrameData data, Action bias) {
        List<Candle> h1Candles = data.getCandles1h();
        if (h1Candles == null || h1Candles.size() < 10) return 0;

        if (bias == Action.BUY) {
            return swingDetector.nearestSupport(h1Candles, data.getCurrentPrice(), 3);
        } else {
            return swingDetector.nearestResistance(h1Candles, data.getCurrentPrice(), 3);
        }
    }

    private boolean hasEntryTrigger(IndicatorSnapshot m15, Action bias) {
        if (bias == Action.BUY) {
            // RSI pulling back (not overbought), volume above average, MACD histogram positive
            return m15.rsi14() < RSI_PULLBACK_LONG
                && m15.volumeRatio() > VOLUME_CONFIRMATION
                && m15.macdHistogram() > 0;
        } else {
            return m15.rsi14() > RSI_PULLBACK_SHORT
                && m15.volumeRatio() > VOLUME_CONFIRMATION
                && m15.macdHistogram() < 0;
        }
    }

    private double calculateBaseConfidence(IndicatorSnapshot h4, IndicatorSnapshot h1, IndicatorSnapshot m15) {
        double confidence = 0.5;

        // Strong 4H trend (ADX > 25)
        if (h4.adx() > 25) confidence += 0.1;

        // 1H aligned (same EMA slope direction as 4H)
        if (Math.signum(h1.ema21Slope()) == Math.signum(h4.ema21Slope())) confidence += 0.1;

        // Strong 15M volume
        if (m15.volumeRatio() > 1.5) confidence += 0.05;

        // MACD histogram growing
        if (Math.abs(m15.macdHistogram()) > Math.abs(m15.macdSignal()) * 0.5) confidence += 0.05;

        return Math.min(0.9, confidence);
    }

    private double calculateFundingBoost(double fundingRate, Action bias) {
        // Negative funding + long = boost (shorts are paying, crowd is short)
        // Positive funding + short = boost (longs are paying, crowd is long)
        if (bias == Action.BUY && fundingRate < -0.005) {
            return FUNDING_CONFIDENCE_BOOST;
        }
        if (bias == Action.SELL && fundingRate > 0.005) {
            return FUNDING_CONFIDENCE_BOOST;
        }
        return 0;
    }

    private String buildBiasExplanation(IndicatorSnapshot h4, Action bias) {
        return String.format("4H EMA21 slope: %.2f (%s). EMA21 (%.2f) %s EMA50 (%.2f). ADX: %.1f. %s regime confirmed.",
            h4.ema21Slope(),
            bias == Action.BUY ? "rising" : "falling",
            h4.ema21(),
            bias == Action.BUY ? "above" : "below",
            h4.ema50(),
            h4.adx(),
            bias == Action.BUY ? "Bullish" : "Bearish");
    }

    private String buildTriggerExplanation(IndicatorSnapshot m15, Action bias) {
        return String.format("15M entry: RSI at %.1f (%s), volume %.1fx average, MACD histogram %.4f (%s). Entry confirmed.",
            m15.rsi14(),
            bias == Action.BUY ? "pulling back from overbought" : "bouncing from oversold",
            m15.volumeRatio(),
            m15.macdHistogram(),
            m15.macdHistogram() > 0 ? "bullish momentum" : "bearish momentum");
    }

    private String buildFundingExplanation(double fundingRate, Action bias, double boost) {
        String direction = fundingRate > 0 ? "positive (longs paying shorts)" : fundingRate < 0 ? "negative (shorts paying longs)" : "neutral";
        String impact = boost > 0 ? "Supports our trade — crowd positioned against us." : "Neutral — no additional edge from funding.";
        return String.format("Funding rate: %.4f (%s). %s", fundingRate, direction, impact);
    }
}
