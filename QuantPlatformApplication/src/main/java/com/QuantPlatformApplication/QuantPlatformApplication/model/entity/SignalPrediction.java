package com.QuantPlatformApplication.QuantPlatformApplication.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * JPA entity for signal predictions mapped to the {@code signal_predictions} table.
 *
 * <p>Tracks strategy signal predictions vs actual returns for IC computation.
 * Records are created at signal time and resolved the next trading day.
 */
@Entity
@Table(name = "signal_predictions")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SignalPrediction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "strategy_id", nullable = false)
    private Long strategyId;

    @Column(nullable = false, length = 20)
    private String symbol;

    /** 1 = up, -1 = down */
    @Column(name = "predicted_direction")
    private Integer predictedDirection;

    @Column(name = "predicted_confidence", precision = 10, scale = 4)
    private BigDecimal predictedConfidence;

    /** Filled in next day — the actual return realized */
    @Column(name = "actual_return", precision = 10, scale = 6)
    private BigDecimal actualReturn;

    /** Filled in next day — was the direction correct? */
    @Column(name = "signal_correct")
    private Boolean signalCorrect;

    @Column(name = "created_at")
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "resolved_at")
    private Instant resolvedAt;
}
