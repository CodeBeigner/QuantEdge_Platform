package com.QuantPlatformApplication.QuantPlatformApplication.model.entity;

import com.QuantPlatformApplication.QuantPlatformApplication.engine.model.ModelType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * JPA entity mapped to the `strategies` table.

 * Stores user-defined trading strategy configurations. The service layer
 * converts this entity into the engine's StrategyConfig for execution.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder

@Entity
@Table(name = "strategies")
public class Strategy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, length = 20)
    private String symbol;

    @Enumerated(EnumType.STRING)
    @Column(name = "model_type", nullable = false, length = 50)
    private ModelType modelType;

    @Column(name = "current_cash", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal currentCash = new BigDecimal("100000.00");

    @Column(name = "position_multiplier", precision = 8, scale = 4)
    @Builder.Default
    private BigDecimal positionMultiplier = new BigDecimal("1.0");

    @Column(name = "target_risk", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal targetRisk = new BigDecimal("10000.00");

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    @Column(name = "created_at")
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at")
    @Builder.Default
    private Instant updatedAt = Instant.now();
}
