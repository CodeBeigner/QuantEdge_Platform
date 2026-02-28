package com.QuantPlatformApplication.QuantPlatformApplication.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "backtest_results")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BacktestResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "strategy_id", nullable = false)
    private Long strategyId;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "initial_capital", nullable = false)
    private BigDecimal initialCapital;

    @Column(name = "final_capital", nullable = false)
    private BigDecimal finalCapital;

    @Column(name = "total_return")
    private BigDecimal totalReturn;

    @Column(name = "sharpe_ratio")
    private BigDecimal sharpeRatio;

    @Column(name = "max_drawdown")
    private BigDecimal maxDrawdown;

    @Column(name = "win_rate")
    private BigDecimal winRate;

    @Column(name = "total_trades")
    @Builder.Default
    private Integer totalTrades = 0;

    @Column(name = "equity_curve", columnDefinition = "jsonb")
    private String equityCurve;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
