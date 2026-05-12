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

    // Backtest realism parameters (added in Plan 3 Task 7)
    private double slippageBps = 5.0;        // 5 bps per side (matches BacktestConfig default)
    private double makerFeePct = 0.0003;     // 3 bps (matches BacktestConfig default)
    private double takerFeePct = 0.0007;     // 7 bps (matches BacktestConfig default)
    private boolean useMakerOrders = true;   // Default to maker pricing
}
