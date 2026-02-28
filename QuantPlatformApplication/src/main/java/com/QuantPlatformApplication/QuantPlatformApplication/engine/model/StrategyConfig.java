package com.QuantPlatformApplication.QuantPlatformApplication.engine.model;

import lombok.Getter;
import lombok.Setter;

/**
 * Configuration for a trading strategy execution.
 * Named StrategyConfig (not Strategy) to avoid clashing with the JPA Strategy
 * entity.

 * In production, this would typically be populated from the Strategy JPA entity
 * or received as a DTO from the controller layer.
 */
@Getter
@Setter
public class StrategyConfig {

    private long id;
    private String name;
    private ModelType modelType;
    private String symbol;
    private double currentCash = 100_000;
    private double positionMultiplier = 1.0;
    private double targetRisk = 10_000;
}
