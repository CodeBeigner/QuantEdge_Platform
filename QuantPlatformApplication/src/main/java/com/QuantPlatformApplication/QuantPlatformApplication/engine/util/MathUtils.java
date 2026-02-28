package com.QuantPlatformApplication.QuantPlatformApplication.engine.util;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;

import java.util.ArrayList;
import java.util.List;

/**
 * Financial math utilities used across all strategy implementations.
 * All methods are static, null-safe, and handle edge cases (empty lists, zero
 * divisors).
 */
public final class MathUtils {

    private MathUtils() {
        // utility class — prevent instantiation
    }

    // ── Simple Moving Average ────────────────────────────────────────────────

    public static double calculateSMA(List<Double> prices, int period) {
        if (prices == null || prices.size() < period)
            return 0;
        return prices.stream()
                .skip(prices.size() - period)
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0);
    }

    // ── Log Returns ──────────────────────────────────────────────────────────

    public static List<Double> calculateReturns(List<Double> prices) {
        if (prices == null || prices.size() < 2)
            return List.of();
        List<Double> returns = new ArrayList<>(prices.size() - 1);
        for (int i = 1; i < prices.size(); i++) {
            if (prices.get(i - 1) > 0) {
                returns.add(Math.log(prices.get(i) / prices.get(i - 1)));
            }
        }
        return returns;
    }

    // ── Annualized Volatility ────────────────────────────────────────────────

    public static double calculateVolatility(List<Double> returns, int window) {
        if (returns == null || returns.size() < window)
            return 0;
        DescriptiveStatistics stats = new DescriptiveStatistics();
        returns.stream().skip(returns.size() - window).forEach(stats::addValue);
        return stats.getStandardDeviation() * Math.sqrt(252) * 100; // annualized %
    }

    // ── Rolling Volatility Series ────────────────────────────────────────────

    public static List<Double> calculateRollingVolatility(List<Double> returns, int window) {
        List<Double> vols = new ArrayList<>();
        for (int i = 0; i < returns.size(); i++) {
            if (i < window - 1) {
                vols.add(Double.NaN);
            } else {
                DescriptiveStatistics stats = new DescriptiveStatistics();
                returns.subList(i - window + 1, i + 1).forEach(stats::addValue);
                vols.add(stats.getStandardDeviation() * Math.sqrt(252) * 100);
            }
        }
        return vols;
    }

    // ── Average (NaN-safe) ───────────────────────────────────────────────────

    public static double calculateAverage(List<Double> values) {
        return values.stream()
                .filter(v -> !Double.isNaN(v))
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0);
    }

    // ── Position Sizing ──────────────────────────────────────────────────────

    public static int calculatePositionSize(double cash, double price, double multiplier) {
        if (price <= 0)
            return 0;
        return (int) Math.floor((cash * 0.95 * multiplier) / price);
    }

    // ── Pearson Correlation ──────────────────────────────────────────────────

    /**
     * Pearson correlation coefficient over the last {@code window} observations.
     * Returns 0 if insufficient data.
     */
    public static double calculateCorrelation(List<Double> r1, List<Double> r2, int window) {
        int n = Math.min(r1.size(), r2.size());
        if (n < window)
            return 0;

        List<Double> a = r1.subList(n - window, n);
        List<Double> b = r2.subList(n - window, n);

        double meanA = a.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double meanB = b.stream().mapToDouble(Double::doubleValue).average().orElse(0);

        double num = 0, denA = 0, denB = 0;
        for (int i = 0; i < window; i++) {
            double dA = a.get(i) - meanA;
            double dB = b.get(i) - meanB;
            num += dA * dB;
            denA += dA * dA;
            denB += dB * dB;
        }

        double denom = Math.sqrt(denA * denB);
        return denom == 0 ? 0 : num / denom;
    }
}
