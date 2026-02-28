package com.QuantPlatformApplication.QuantPlatformApplication.model.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Getter
@Builder
public class BacktestResponse {
    private Long id;
    private Long strategyId;
    private LocalDate startDate;
    private LocalDate endDate;
    private BigDecimal initialCapital;
    private BigDecimal finalCapital;
    private BigDecimal totalReturn;
    private BigDecimal sharpeRatio;
    private BigDecimal maxDrawdown;
    private BigDecimal winRate;
    private Integer totalTrades;
    private List<Double> equityCurve;
    private Instant createdAt;
}
