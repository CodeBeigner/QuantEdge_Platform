package com.QuantPlatformApplication.QuantPlatformApplication.engine.strategy;

import com.QuantPlatformApplication.QuantPlatformApplication.engine.model.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

/**
 * Mean Reversion Strategy — Multi-Timeframe.
 *
 * Trades price extremes with reversion to the mean.
 *
 * 4H: Regime detection — ADX < 20 = ranging (preferred). Also trades extreme extensions in trends.
 * 1H: Extreme detection — RSI > 80 AND Bollinger %B > 1.0 (overbought) or RSI < 20 AND %B < 0.0 (oversold).
 *      Volume spike > 2x average confirms climactic move.
 * 15M: Reversal confirmation — volume declining (exhaustion) or RSI divergence.
 * Funding: Extreme positive funding + overbought = HIGH conviction short.
 *          Extreme negative funding + oversold = HIGH conviction long.
 * Take-profit: VWAP or Bollinger middle band (the "mean" target).
 * Stop: Beyond the extreme (above high for shorts, below low for longs).
 * Time stop: 8 candles (2 hours on 15M) — noted in metadata, not enforced in signal.
 */
@Slf4j
@Component
public class MeanReversionStrategy implements MultiTimeFrameStrategy {

    // 1H extreme thresholds
    private static final double RSI_OVERBOUGHT = 80;
    private static final double RSI_OVERSOLD = 20;
    private static final double BB_PERCENTB_OVERBOUGHT = 1.0;
    private static final double BB_PERCENTB_OVERSOLD = 0.0;
    private static final double CLIMACTIC_VOLUME_RATIO = 2.0;

    // 4H regime
    private static final double ADX_RANGING_THRESHOLD = 20;

    // Funding thresholds
    private static final double FUNDING_EXTREME_POSITIVE = 0.05;
    private static final double FUNDING_EXTREME_NEGATIVE = -0.03;
    private static final double FUNDING_CONFIDENCE_BOOST = 0.15;

    // Time stop (noted in metadata)
    private static final int TIME_STOP_CANDLES = 8;

    @Override
    public ModelType getModelType() {
        return ModelType.MEAN_REVERSION;
    }

    @Override
    public String getName() {
        return "Mean Reversion";
    }

    @Override
    public Optional<TradeSignal> analyze(MultiTimeFrameData data) {
        if (!data.isComplete()) {
            return Optional.empty();
        }

        IndicatorSnapshot h4 = data.indicators(TimeFrame.H4);
        IndicatorSnapshot h1 = data.indicators(TimeFrame.H1);
        IndicatorSnapshot m15 = data.indicators(TimeFrame.M15);

        // Step 1: Detect extreme on 1H
        ExtremeType extreme = detectExtreme(h1);
        if (extreme == ExtremeType.NONE) {
            return Optional.empty();
        }

        // Step 2: Check 4H regime context
        boolean isRanging = h4.adx() < ADX_RANGING_THRESHOLD;
        String regimeNote = isRanging
            ? "4H ADX %.1f — ranging market, ideal for mean reversion."
            : "4H ADX %.1f — trending market, but extreme extension detected.";
        regimeNote = String.format(regimeNote, h4.adx());

        // Step 3: 15M reversal confirmation
        // We look for exhaustion signs: the 15M should show some divergence or lower momentum
        // than the 1H extreme. Not blocking the trade, but adjusting confidence.
        boolean hasReversalConfirmation = checkReversalConfirmation(m15, extreme);

        // Step 4: Determine action and build signal
        Action action = extreme == ExtremeType.OVERBOUGHT ? Action.SELL : Action.BUY;
        double entry = data.getCurrentPrice();

        // Take-profit: target the "mean" — closer of VWAP or Bollinger middle
        double vwap = h1.vwap();
        double bbMid = h1.bollingerMiddle();
        double meanTarget;
        if (action == Action.SELL) {
            // For shorts, pick the higher of VWAP/BB mid (closer to entry = more conservative)
            meanTarget = Math.max(vwap, bbMid);
        } else {
            // For longs, pick the lower of VWAP/BB mid (closer to entry = more conservative)
            meanTarget = Math.min(vwap, bbMid);
        }
        double takeProfit = meanTarget;

        // Stop: beyond the extreme using ATR
        double atr = m15.atr14();
        double stopLoss;
        if (action == Action.SELL) {
            // Stop above the overbought extreme
            stopLoss = entry + atr * 1.5;
        } else {
            // Stop below the oversold extreme
            stopLoss = entry - atr * 1.5;
        }

        // Step 5: Calculate confidence
        double confidence = calculateConfidence(h4, h1, m15, extreme, hasReversalConfirmation,
            data.getFundingRate());

        // Build explanations
        String biasExplanation = String.format(
            "%s 1H RSI at %.1f with Bollinger %%B at %.2f — price at statistical extreme. %s",
            regimeNote, h1.rsi14(), h1.bollingerPercentB(),
            h1.volumeRatio() >= CLIMACTIC_VOLUME_RATIO
                ? String.format("Volume spike %.1fx average confirms climactic move.", h1.volumeRatio())
                : "No climactic volume spike.");

        String zoneExplanation = String.format(
            "Price is %s Bollinger Bands (%%B=%.2f). Mean target: VWAP=%.2f, BB mid=%.2f.",
            extreme == ExtremeType.OVERBOUGHT ? "above upper" : "below lower",
            h1.bollingerPercentB(), vwap, bbMid);

        String triggerExplanation = String.format(
            "15M RSI at %.1f, volume %.1fx average. %s",
            m15.rsi14(), m15.volumeRatio(),
            hasReversalConfirmation
                ? "Reversal confirmation detected — momentum exhaustion on lower timeframe."
                : "Entering on 1H extreme without full 15M confirmation — reduced confidence.");

        String fundingExplanation = buildFundingExplanation(data.getFundingRate(), action);

        String lesson = action == Action.SELL
            ? "Mean reversion: when RSI exceeds 80 and price pushes beyond Bollinger upper band, "
              + "statistical likelihood favors a pullback to the mean (VWAP or middle band). "
              + "Extreme positive funding confirms overcrowded longs — fuel for a snapback."
            : "Mean reversion: when RSI drops below 20 and price falls below Bollinger lower band, "
              + "the market is statistically oversold. Extreme negative funding means shorts are overcrowded "
              + "and paying hefty rates — a snapback squeeze is likely.";

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
            .riskExplanation(String.format("Entry: %.2f | Stop: %.2f | TP: %.2f (mean target)",
                entry, stopLoss, takeProfit))
            .lesson(lesson)
            .metadata(Map.of(
                "h1_rsi", h1.rsi14(),
                "h1_bb_percentB", h1.bollingerPercentB(),
                "h1_volume_ratio", h1.volumeRatio(),
                "h4_adx", h4.adx(),
                "funding_rate", data.getFundingRate(),
                "extreme_type", extreme.name(),
                "time_stop_candles", TIME_STOP_CANDLES,
                "reversal_confirmed", hasReversalConfirmation
            ))
            .build();

        log.info("MEAN_REVERSION signal: {} {} @ {} (conf: {}, extreme: {})",
            signal.getAction(), data.getSymbol(), entry, confidence, extreme);

        return Optional.of(signal);
    }

    private ExtremeType detectExtreme(IndicatorSnapshot h1) {
        boolean overbought = h1.rsi14() > RSI_OVERBOUGHT && h1.bollingerPercentB() > BB_PERCENTB_OVERBOUGHT;
        boolean oversold = h1.rsi14() < RSI_OVERSOLD && h1.bollingerPercentB() < BB_PERCENTB_OVERSOLD;

        if (overbought) return ExtremeType.OVERBOUGHT;
        if (oversold) return ExtremeType.OVERSOLD;
        return ExtremeType.NONE;
    }

    private boolean checkReversalConfirmation(IndicatorSnapshot m15, ExtremeType extreme) {
        if (extreme == ExtremeType.OVERBOUGHT) {
            // For overbought reversal: 15M RSI should be declining from the 1H extreme
            // (not still pushing higher aggressively) and volume should show exhaustion
            return m15.rsi14() < RSI_OVERBOUGHT && m15.volumeRatio() > 1.0;
        } else {
            // For oversold reversal: 15M RSI should be rising from the 1H extreme
            return m15.rsi14() > RSI_OVERSOLD && m15.volumeRatio() > 1.0;
        }
    }

    private double calculateConfidence(IndicatorSnapshot h4, IndicatorSnapshot h1,
                                        IndicatorSnapshot m15, ExtremeType extreme,
                                        boolean hasReversalConfirmation, double fundingRate) {
        double confidence = 0.5;

        // Ranging regime boosts confidence (mean reversion works best in ranges)
        if (h4.adx() < ADX_RANGING_THRESHOLD) {
            confidence += 0.05;
        }

        // Climactic volume on 1H (volume spike at extreme)
        if (h1.volumeRatio() >= CLIMACTIC_VOLUME_RATIO) {
            confidence += 0.1;
        }

        // 15M reversal confirmation
        if (hasReversalConfirmation) {
            confidence += 0.05;
        }

        // Funding alignment — the big confidence booster
        if (extreme == ExtremeType.OVERBOUGHT && fundingRate > FUNDING_EXTREME_POSITIVE) {
            confidence += FUNDING_CONFIDENCE_BOOST;
        } else if (extreme == ExtremeType.OVERSOLD && fundingRate < FUNDING_EXTREME_NEGATIVE) {
            confidence += FUNDING_CONFIDENCE_BOOST;
        }

        return Math.min(1.0, confidence);
    }

    private String buildFundingExplanation(double fundingRate, Action action) {
        if (action == Action.SELL && fundingRate > FUNDING_EXTREME_POSITIVE) {
            return String.format("Funding rate: %.4f — extreme positive. Longs paying %.2f%% per 8h. "
                + "Overcrowded long positions provide fuel for mean reversion short.",
                fundingRate, fundingRate * 100);
        } else if (action == Action.BUY && fundingRate < FUNDING_EXTREME_NEGATIVE) {
            return String.format("Funding rate: %.4f — extreme negative. Shorts paying %.2f%% per 8h. "
                + "Overcrowded short positions provide fuel for mean reversion long.",
                fundingRate, Math.abs(fundingRate) * 100);
        } else {
            return String.format("Funding rate: %.4f — no extreme alignment. "
                + "Signal based purely on price extreme.", fundingRate);
        }
    }

    private enum ExtremeType {
        OVERBOUGHT, OVERSOLD, NONE
    }
}
