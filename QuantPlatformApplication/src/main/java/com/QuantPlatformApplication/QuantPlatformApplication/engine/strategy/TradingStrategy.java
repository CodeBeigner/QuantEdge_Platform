package com.QuantPlatformApplication.QuantPlatformApplication.engine.strategy;

import com.QuantPlatformApplication.QuantPlatformApplication.engine.model.ExecutionResult;
import com.QuantPlatformApplication.QuantPlatformApplication.engine.model.MarketData;
import com.QuantPlatformApplication.QuantPlatformApplication.engine.model.ModelType;
import com.QuantPlatformApplication.QuantPlatformApplication.engine.model.StrategyConfig;

/**
 * Common interface for all trading strategy implementations.
 * Each strategy is a Spring @Component and is auto-discovered by the
 * StrategyExecutor.
 */
public interface TradingStrategy {

    /**
     * Execute the strategy against the given market data.
     */
    ExecutionResult execute(StrategyConfig strategy, MarketData data);

    /**
     * Which model type this strategy handles.
     */
    ModelType getModelType();
}
