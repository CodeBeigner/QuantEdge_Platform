package com.QuantPlatformApplication.QuantPlatformApplication.model.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "risk_config")
public class RiskConfig {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Builder.Default
    @Column(name = "risk_per_trade_pct", nullable = false)
    private BigDecimal riskPerTradePct = new BigDecimal("0.01");

    @Builder.Default
    @Column(name = "max_effective_leverage", nullable = false)
    private BigDecimal maxEffectiveLeverage = new BigDecimal("5.0");

    @Builder.Default
    @Column(name = "daily_loss_halt_pct", nullable = false)
    private BigDecimal dailyLossHaltPct = new BigDecimal("0.05");

    @Builder.Default
    @Column(name = "max_drawdown_pct", nullable = false)
    private BigDecimal maxDrawdownPct = new BigDecimal("0.15");

    @Builder.Default
    @Column(name = "max_concurrent_positions", nullable = false)
    private Integer maxConcurrentPositions = 3;

    @Builder.Default
    @Column(name = "max_stop_distance_pct", nullable = false)
    private BigDecimal maxStopDistancePct = new BigDecimal("0.02");

    @Builder.Default
    @Column(name = "min_risk_reward_ratio", nullable = false)
    private BigDecimal minRiskRewardRatio = new BigDecimal("1.5");

    @Builder.Default
    @Column(name = "fee_impact_threshold", nullable = false)
    private BigDecimal feeImpactThreshold = new BigDecimal("0.20");

    @Builder.Default
    @Column(name = "execution_mode", nullable = false)
    private String executionMode = "HUMAN_IN_LOOP";

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() { createdAt = Instant.now(); updatedAt = Instant.now(); }

    @PreUpdate
    void preUpdate() { updatedAt = Instant.now(); }
}
