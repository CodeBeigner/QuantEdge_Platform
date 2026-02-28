package com.QuantPlatformApplication.QuantPlatformApplication.model.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.Instant;

@Data
@Entity
@Table(name = "portfolio_positions")
public class PortfolioPosition {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private String symbol;
    private Integer quantity;
    private BigDecimal avgCost;
    private BigDecimal currentPrice;
    private BigDecimal unrealizedPnl;
    private BigDecimal realizedPnl;
    private BigDecimal weight;
    private Instant updatedAt;

    @PrePersist
    @PreUpdate
    void onSave() {
        updatedAt = Instant.now();
    }
}
