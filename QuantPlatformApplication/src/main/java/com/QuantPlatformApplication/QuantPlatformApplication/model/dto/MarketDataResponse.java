package com.QuantPlatformApplication.QuantPlatformApplication.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Response DTO for market data API endpoints.

 * Why a DTO instead of returning the entity directly?
 * - Decouples API contract from database schema
 * - Prevents leaking JPA internals (lazy loading proxies, etc.)
 * - Allows formatting changes without touching the entity
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MarketDataResponse {

    private Instant time;
    private String symbol;
    private BigDecimal open;
    private BigDecimal high;
    private BigDecimal low;
    private BigDecimal close;
    private Long volume;
}
