package com.QuantPlatformApplication.QuantPlatformApplication.service;

import com.QuantPlatformApplication.QuantPlatformApplication.model.entity.SignalPrediction;
import com.QuantPlatformApplication.QuantPlatformApplication.repository.SignalPredictionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for tracking signal information coefficients (IC) and detecting drift.
 *
 * <p>After each trading day, compares each strategy's signal prediction against
 * the actual next-day return to compute rolling IC metrics.
 *
 * <p>IC is the Pearson correlation between predicted direction and actual return.
 * Drift is detected when IC drops below critical thresholds.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SignalTrackingService {

    private final SignalPredictionRepository predictionRepo;

    /** IC below this threshold triggers a drift alert */
    private static final double IC_DRIFT_THRESHOLD = 0.02;

    /**
     * Record a new signal prediction for IC tracking.
     *
     * @param strategyId      the strategy that generated the signal
     * @param symbol          the target symbol
     * @param predictedReturn predicted return direction (1=up, -1=down)
     * @param confidence      signal confidence (0-1)
     * @param timestamp       when the signal was generated
     */
    @Transactional
    public void recordSignalPrediction(Long strategyId, String symbol,
            double predictedReturn, double confidence, Instant timestamp) {
        SignalPrediction prediction = SignalPrediction.builder()
                .strategyId(strategyId)
                .symbol(symbol)
                .predictedDirection(predictedReturn >= 0 ? 1 : -1)
                .predictedConfidence(BigDecimal.valueOf(confidence))
                .createdAt(timestamp)
                .build();
        predictionRepo.save(prediction);
        log.debug("Recorded signal prediction: strategyId={}, symbol={}, direction={}",
                strategyId, symbol, prediction.getPredictedDirection());
    }

    /**
     * Record the actual return for a prediction (called next trading day).
     *
     * @param predictionId the prediction to resolve
     * @param actualReturn the realized return
     */
    @Transactional
    public void recordActualReturn(Long predictionId, double actualReturn) {
        predictionRepo.findById(predictionId).ifPresent(p -> {
            p.setActualReturn(BigDecimal.valueOf(actualReturn));
            p.setSignalCorrect(
                    (p.getPredictedDirection() > 0 && actualReturn > 0)
                 || (p.getPredictedDirection() < 0 && actualReturn < 0));
            p.setResolvedAt(Instant.now());
            predictionRepo.save(p);
        });
    }

    /**
     * Compute rolling Information Coefficient (IC) for a strategy.
     *
     * <p>IC = Pearson correlation between predicted direction and actual return
     * over the specified rolling window.
     *
     * @param strategyId the strategy to analyze
     * @param windowDays rolling window size in days
     * @return IC value (-1 to 1), or 0 if insufficient data
     */
    @Transactional(readOnly = true)
    public double computeRollingIC(Long strategyId, int windowDays) {
        Instant since = Instant.now().minus(windowDays, ChronoUnit.DAYS);
        List<SignalPrediction> predictions = predictionRepo.findResolvedByStrategy(strategyId, since);

        if (predictions.size() < 5) {
            return 0.0;
        }

        // Compute Pearson correlation between predicted direction and actual return
        double[] x = new double[predictions.size()];
        double[] y = new double[predictions.size()];
        for (int i = 0; i < predictions.size(); i++) {
            x[i] = predictions.get(i).getPredictedDirection();
            y[i] = predictions.get(i).getActualReturn().doubleValue();
        }

        return pearsonCorrelation(x, y);
    }

    /**
     * Detect signal drift for a strategy.
     *
     * <p>Computes 20-day and 60-day IC values and flags drift if either
     * drops below the critical threshold (0.02).
     *
     * @param strategyId the strategy to analyze
     * @return drift detection result with IC values and alert status
     */
    @Transactional(readOnly = true)
    public Map<String, Object> detectDrift(Long strategyId) {
        double ic20 = computeRollingIC(strategyId, 20);
        double ic60 = computeRollingIC(strategyId, 60);
        boolean isDrifting = ic20 < IC_DRIFT_THRESHOLD;
        boolean alertTriggered = isDrifting;

        if (alertTriggered) {
            log.warn("Signal drift detected for strategyId={}: IC20={}, IC60={}",
                    strategyId, ic20, ic60);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("strategy_id", strategyId);
        result.put("is_drifting", isDrifting);
        result.put("ic_20d", Math.round(ic20 * 10000.0) / 10000.0);
        result.put("ic_60d", Math.round(ic60 * 10000.0) / 10000.0);
        result.put("alert_triggered", alertTriggered);
        result.put("threshold", IC_DRIFT_THRESHOLD);
        return result;
    }

    /**
     * Compute Pearson correlation coefficient between two arrays.
     */
    private double pearsonCorrelation(double[] x, double[] y) {
        int n = x.length;
        double sumX = 0, sumY = 0, sumXY = 0, sumX2 = 0, sumY2 = 0;

        for (int i = 0; i < n; i++) {
            sumX += x[i];
            sumY += y[i];
            sumXY += x[i] * y[i];
            sumX2 += x[i] * x[i];
            sumY2 += y[i] * y[i];
        }

        double numerator = n * sumXY - sumX * sumY;
        double denominator = Math.sqrt(
                (n * sumX2 - sumX * sumX) * (n * sumY2 - sumY * sumY));

        if (denominator == 0) return 0;
        return numerator / denominator;
    }
}
