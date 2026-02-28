package com.QuantPlatformApplication.QuantPlatformApplication.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * JPA entity mapped to the `market_data` table.
 * Stores OHLCV (Open, High, Low, Close, Volume) price data for a given symbol
 * and timestamp.

 * This is DIFFERENT from the engine's MarketData class
 * (engine/model/MarketData.java):
 * - This entity represents a single DB row (one bar of data).
 * - The engine's MarketData is a computation-focused POJO holding lists of
 * prices and indicators.
 * - The service layer converts between them.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder

@Entity
@Table(name = "market_data")
@IdClass(MarketDataId.class)
public class MarketDataEntity {

    @Id
    @Column(nullable = false)
    private Instant time;

    @Id
    @Column(nullable = false, length = 20)
    private String symbol;

    @Column(precision = 15, scale = 4)
    private BigDecimal open;

    @Column(precision = 15, scale = 4)
    private BigDecimal high;

    @Column(precision = 15, scale = 4)
    private BigDecimal low;

    @Column(name = "close", precision = 15, scale = 4)
    private BigDecimal close;

    private Long volume;
}
