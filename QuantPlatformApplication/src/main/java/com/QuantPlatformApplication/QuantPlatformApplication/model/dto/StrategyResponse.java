package com.QuantPlatformApplication.QuantPlatformApplication.model.dto;

import com.QuantPlatformApplication.QuantPlatformApplication.engine.model.ModelType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Outbound DTO for strategy CRUD responses.
 * Decouples the API contract from the JPA entity.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StrategyResponse {

    private Long id;
    private String name;
    private String symbol;
    private ModelType modelType;
    private BigDecimal currentCash;
    private BigDecimal positionMultiplier;
    private BigDecimal targetRisk;
    private Boolean active;
    private Instant createdAt;
    private Instant updatedAt;
}
