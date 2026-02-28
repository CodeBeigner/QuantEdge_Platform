package com.QuantPlatformApplication.QuantPlatformApplication.model.dto;

import com.QuantPlatformApplication.QuantPlatformApplication.engine.model.ModelType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Inbound DTO for creating or updating a strategy.
 * Uses Jakarta Validation to enforce constraints at the controller layer.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StrategyRequest {

    @NotBlank(message = "Strategy name is required")
    private String name;

    @NotBlank(message = "Symbol is required")
    private String symbol;

    @NotNull(message = "Model type is required (MOMENTUM, VOLATILITY, MACRO, CORRELATION, REGIME)")
    private ModelType modelType;

    @Positive(message = "Current cash must be positive")
    private Double currentCash = 100_000.0;

    @Positive(message = "Position multiplier must be positive")
    private Double positionMultiplier = 1.0;

    @Positive(message = "Target risk must be positive")
    private Double targetRisk = 10_000.0;
}
