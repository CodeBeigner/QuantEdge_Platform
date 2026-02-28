package com.QuantPlatformApplication.QuantPlatformApplication.service;

import com.QuantPlatformApplication.QuantPlatformApplication.model.entity.Alert;
import com.QuantPlatformApplication.QuantPlatformApplication.model.entity.MarketDataEntity;
import com.QuantPlatformApplication.QuantPlatformApplication.model.entity.PortfolioPosition;
import com.QuantPlatformApplication.QuantPlatformApplication.repository.AlertRepository;
import com.QuantPlatformApplication.QuantPlatformApplication.repository.MarketDataRepository;
import com.QuantPlatformApplication.QuantPlatformApplication.repository.PortfolioPositionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * Risk Management Engine — VaR, drawdown detection, position limits.
 *
 * Clean Architecture: Service layer only. No direct HTTP or DB access patterns
 * outside the designated repositories.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RiskEngineService {

    private final MarketDataRepository marketDataRepo;
    private final PortfolioPositionRepository positionRepo;
    private final AlertRepository alertRepo;

    // Configurable risk limits
    private static final BigDecimal MAX_POSITION_VALUE = new BigDecimal("100000");
    private static final double MAX_DRAWDOWN_PCT = 0.10; // 10%
    private static final double VAR_CONFIDENCE = 0.95;

    /**
     * Calculate Value at Risk (Historical VaR) for a symbol.
     */
    public Map<String, Object> calculateVaR(String symbol, int days) {
        List<MarketDataEntity> data = marketDataRepo.findRecentBySymbol(symbol, days);
        if (data.size() < 20) {
            return Map.of("error", "Insufficient data for VaR calculation");
        }

        // Calculate daily returns
        double[] returns = new double[data.size() - 1];
        for (int i = 1; i < data.size(); i++) {
            double prev = data.get(i - 1).getClose().doubleValue();
            double curr = data.get(i).getClose().doubleValue();
            returns[i - 1] = (curr - prev) / prev;
        }

        Arrays.sort(returns);
        int varIndex = (int) ((1 - VAR_CONFIDENCE) * returns.length);
        double var95 = returns[Math.max(0, varIndex)];

        // Conditional VaR (Expected shortfall)
        double cvar = 0;
        for (int i = 0; i <= varIndex; i++) {
            cvar += returns[i];
        }
        cvar = varIndex > 0 ? cvar / (varIndex + 1) : var95;

        // Calculate max drawdown
        double maxDrawdown = calculateMaxDrawdown(data);

        // Check for breaches and create alerts
        if (Math.abs(var95) > MAX_DRAWDOWN_PCT) {
            createAlert("VAR_BREACH", "CRITICAL", symbol,
                    String.format("VaR(95%%) breach for %s: %.2f%%", symbol, var95 * 100));
        }
        if (maxDrawdown > MAX_DRAWDOWN_PCT) {
            createAlert("DRAWDOWN", "CRITICAL", symbol,
                    String.format("Max drawdown breach for %s: %.2f%%", symbol, maxDrawdown * 100));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("symbol", symbol);
        result.put("var95", round(var95 * 100));
        result.put("cvar95", round(cvar * 100));
        result.put("maxDrawdown", round(maxDrawdown * 100));
        result.put("daysAnalyzed", data.size());
        result.put("breaches", Math.abs(var95) > MAX_DRAWDOWN_PCT || maxDrawdown > MAX_DRAWDOWN_PCT);
        return result;
    }

    /**
     * Calculate max drawdown from price data.
     */
    private double calculateMaxDrawdown(List<MarketDataEntity> data) {
        double peak = 0;
        double maxDD = 0;
        for (MarketDataEntity d : data) {
            double price = d.getClose().doubleValue();
            if (price > peak)
                peak = price;
            double dd = (peak - price) / peak;
            if (dd > maxDD)
                maxDD = dd;
        }
        return maxDD;
    }

    /**
     * Check position limits for all holdings.
     */
    public Map<String, Object> checkPositionLimits() {
        List<PortfolioPosition> positions = positionRepo.findAll();
        List<Map<String, Object>> breaches = new ArrayList<>();

        for (PortfolioPosition pos : positions) {
            BigDecimal value = pos.getCurrentPrice() != null
                    ? pos.getCurrentPrice().multiply(BigDecimal.valueOf(pos.getQuantity()))
                    : BigDecimal.ZERO;
            if (value.compareTo(MAX_POSITION_VALUE) > 0) {
                breaches.add(Map.of(
                        "symbol", pos.getSymbol(),
                        "value", value,
                        "limit", MAX_POSITION_VALUE));
                createAlert("POSITION_LIMIT", "WARNING", pos.getSymbol(),
                        String.format("Position limit breach: %s value $%s exceeds $%s",
                                pos.getSymbol(), value, MAX_POSITION_VALUE));
            }
        }

        return Map.of(
                "totalPositions", positions.size(),
                "breaches", breaches,
                "positionLimit", MAX_POSITION_VALUE,
                "allClear", breaches.isEmpty());
    }

    /**
     * Get portfolio-wide risk metrics.
     */
    public Map<String, Object> getPortfolioRisk() {
        List<PortfolioPosition> positions = positionRepo.findAll();
        BigDecimal totalValue = BigDecimal.ZERO;
        BigDecimal totalPnl = BigDecimal.ZERO;

        for (PortfolioPosition pos : positions) {
            BigDecimal posValue = pos.getCurrentPrice() != null
                    ? pos.getCurrentPrice().multiply(BigDecimal.valueOf(pos.getQuantity()))
                    : BigDecimal.ZERO;
            totalValue = totalValue.add(posValue);
            totalPnl = totalPnl.add(pos.getUnrealizedPnl() != null ? pos.getUnrealizedPnl() : BigDecimal.ZERO);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalPortfolioValue", totalValue);
        result.put("totalUnrealizedPnl", totalPnl);
        result.put("positionCount", positions.size());
        result.put("maxPositionLimit", MAX_POSITION_VALUE);
        result.put("maxDrawdownLimit", MAX_DRAWDOWN_PCT * 100 + "%");
        result.put("varConfidence", VAR_CONFIDENCE * 100 + "%");
        return result;
    }

    private void createAlert(String type, String severity, String symbol, String message) {
        Alert alert = new Alert();
        alert.setAlertType(type);
        alert.setSeverity(severity);
        alert.setSymbol(symbol);
        alert.setMessage(message);
        alertRepo.save(alert);
        log.warn("ALERT [{}] {}: {}", severity, type, message);
    }

    private double round(double v) {
        return BigDecimal.valueOf(v).setScale(4, RoundingMode.HALF_UP).doubleValue();
    }
}
