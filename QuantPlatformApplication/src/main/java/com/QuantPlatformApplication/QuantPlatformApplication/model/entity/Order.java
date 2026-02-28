package com.QuantPlatformApplication.QuantPlatformApplication.model.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.Instant;

@Data
@Entity
@Table(name = "orders")
public class Order {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String symbol;
    private String side; // BUY, SELL
    private String orderType; // MARKET, LIMIT
    private Integer quantity;
    private BigDecimal price;
    private BigDecimal filledPrice;
    private String status; // PENDING, FILLED, CANCELLED, REJECTED
    private Long strategyId;
    private BigDecimal slippage;
    private Instant createdAt;
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = updatedAt = Instant.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
