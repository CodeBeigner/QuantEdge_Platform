package com.QuantPlatformApplication.QuantPlatformApplication.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "trade_logs")
public class TradeLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "trade_id", nullable = false, unique = true, length = 50)
    private String tradeId;

    @Column(nullable = false, length = 20)
    private String symbol;

    @Column(nullable = false, length = 10)
    private String direction;

    @Column(name = "strategy_name", nullable = false, length = 50)
    private String strategyName;

    @Column(name = "entry_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal entryPrice;

    @Column(name = "stop_loss_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal stopLossPrice;

    @Column(name = "take_profit_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal takeProfitPrice;

    @Column(name = "position_size", precision = 19, scale = 8)
    private BigDecimal positionSize;

    @Column(name = "effective_leverage", precision = 6, scale = 2)
    private BigDecimal effectiveLeverage;

    @Column(precision = 4, scale = 3)
    private BigDecimal confidence;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> explanation;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> outcome;

    @Builder.Default
    @Column(nullable = false, length = 20)
    private String status = "PENDING";

    @Builder.Default
    @Column(name = "execution_mode", nullable = false, length = 20)
    private String executionMode = "AUTONOMOUS";

    @Column(name = "opened_at", nullable = false)
    private Instant openedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
        if (openedAt == null) openedAt = Instant.now();
    }

    @PreUpdate
    void preUpdate() { updatedAt = Instant.now(); }
}
