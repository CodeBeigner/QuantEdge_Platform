// ============================================================================
// CONCURRENT STRATEGY EXECUTION ENGINE
// High-Performance Multi-threaded Execution with Java 21 Virtual Threads
//
// Refactored: This class is now a slim orchestrator that delegates to
// TradingStrategy implementations discovered via Spring auto-wiring.
// Domain models → engine/model/
// Strategy logic → engine/strategy/
// Math utilities → engine/util/MathUtils
// ============================================================================

package com.QuantPlatformApplication.QuantPlatformApplication.engine;

import com.QuantPlatformApplication.QuantPlatformApplication.engine.model.ExecutionResult;
import com.QuantPlatformApplication.QuantPlatformApplication.engine.model.MarketData;
import com.QuantPlatformApplication.QuantPlatformApplication.engine.model.ModelType;
import com.QuantPlatformApplication.QuantPlatformApplication.engine.model.StrategyConfig;
import com.QuantPlatformApplication.QuantPlatformApplication.engine.strategy.TradingStrategy;
import com.QuantPlatformApplication.QuantPlatformApplication.service.MarketDataService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * High-performance strategy executor using Java 21 Virtual Threads.
 * Supports 10,000+ concurrent strategies via lightweight virtual threads.

 * Usage:
 * CompletableFuture<ExecutionResult> result =
 * executor.executeAsync(strategy, marketData);
 */
@Slf4j
@Service
public class StrategyExecutor {

    private final ExecutorService virtualThreadExecutor;
    private final ScheduledExecutorService scheduler;
    private final ConcurrentHashMap<Long, CompletableFuture<ExecutionResult>> runningStrategies;
    private final AtomicLong executionCounter;
    private final MarketDataService marketDataService;

    /** Strategy registry — ModelType → TradingStrategy implementation */
    private final Map<ModelType, TradingStrategy> strategyMap;

    // Timeout to prevent runaway strategy executions
    private static final long EXECUTION_TIMEOUT_SECONDS = 30;

    /** How many trading days of data to load for scheduled executions */
    private static final int DEFAULT_LOOKBACK_DAYS = 252;

    /**
     * Spring auto-discovers all @Component classes implementing TradingStrategy
     * and injects them as a list. We build a lookup map by ModelType.
     * MarketDataService is injected to fetch real price data from the DB.
     */
    public StrategyExecutor(List<TradingStrategy> strategies,
            MarketDataService marketDataService) {
        this.virtualThreadExecutor = Executors.newVirtualThreadPerTaskExecutor();
        this.scheduler = Executors.newScheduledThreadPool(10);
        this.runningStrategies = new ConcurrentHashMap<>();
        this.executionCounter = new AtomicLong(0);
        this.marketDataService = marketDataService;

        this.strategyMap = strategies.stream()
                .collect(Collectors.toMap(TradingStrategy::getModelType, Function.identity()));

        log.info("StrategyExecutor initialized with {} strategy types: {}",
                strategyMap.size(), strategyMap.keySet());
    }

    // ========================================================================
    // PUBLIC API
    // ========================================================================

    /**
     * Execute a single strategy asynchronously.
     * Returns a CompletableFuture that completes with the execution result.
     */
    @Async("strategyExecutor")
    public CompletableFuture<ExecutionResult> executeAsync(StrategyConfig strategy, MarketData data) {
        long executionId = executionCounter.incrementAndGet();

        CompletableFuture<ExecutionResult> future = CompletableFuture.supplyAsync(() -> {
            try {
                log.info("Executing strategy {} [type={}] — execution #{}",
                        strategy.getName(), strategy.getModelType(), executionId);

                long start = System.nanoTime();

                TradingStrategy handler = strategyMap.get(strategy.getModelType());
                if (handler == null) {
                    throw new IllegalArgumentException(
                            "No strategy implementation for model type: " + strategy.getModelType());
                }

                ExecutionResult result = handler.execute(strategy, data);

                long elapsedMs = (System.nanoTime() - start) / 1_000_000;
                log.info("Strategy {} completed in {}ms", strategy.getName(), elapsedMs);
                return result;

            } catch (Exception e) {
                log.error("Strategy execution failed for {}: {}", strategy.getName(), e.getMessage(), e);
                return ExecutionResult.failure(strategy.getId(), e.getMessage());
            }
        }, virtualThreadExecutor);

        // Apply timeout protection
        future = future.orTimeout(EXECUTION_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        runningStrategies.put(executionId, future);
        future.whenComplete((result, throwable) -> {
            runningStrategies.remove(executionId);
            if (throwable != null) {
                log.error("Strategy execution #{} error: {}", executionId, throwable.getMessage());
            }
        });

        return future;
    }

    /**
     * Execute multiple strategies in parallel, wait for all to finish.
     */
    public List<ExecutionResult> executeBatch(List<StrategyConfig> strategies, MarketData data) {
        List<CompletableFuture<ExecutionResult>> futures = strategies.stream()
                .map(s -> executeAsync(s, data))
                .toList();

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        return futures.stream()
                .map(CompletableFuture::join)
                .toList();
    }

    /**
     * Schedule a strategy to run at a fixed interval.
     */
    public ScheduledFuture<?> scheduleAtFixedRate(StrategyConfig strategy,
            long initialDelay,
            long period,
            TimeUnit unit) {
        return scheduler.scheduleAtFixedRate(() -> {
            try {
                MarketData currentData = fetchCurrentMarketData(strategy.getSymbol());
                executeAsync(strategy, currentData);
            } catch (Exception e) {
                log.error("Scheduled execution failed for strategy {}: {}", strategy.getId(), e.getMessage());
            }
        }, initialDelay, period, unit);
    }

    /**
     * Graceful shutdown — wait for running strategies to complete.
     */
    public void shutdown() {
        log.info("Shutting down StrategyExecutor...");
        virtualThreadExecutor.shutdown();
        scheduler.shutdown();
        try {
            if (!virtualThreadExecutor.awaitTermination(60, TimeUnit.SECONDS)) {
                virtualThreadExecutor.shutdownNow();
            }
            if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        log.info("StrategyExecutor shut down. {} strategies were running.", runningStrategies.size());
    }

    /** How many strategies are currently executing? */
    public int getRunningCount() {
        return runningStrategies.size();
    }

    // ========================================================================
    // INTERNAL
    // ========================================================================

    /**
     * Fetch real market data from the database for scheduled executions.
     * Loads the most recent DEFAULT_LOOKBACK_DAYS of close prices and
     * converts them into the engine's MarketData format.
     */
    private MarketData fetchCurrentMarketData(String symbol) {
        var entities = marketDataService.fetchRecentData(symbol, DEFAULT_LOOKBACK_DAYS);
        MarketData data = new MarketData();
        if (!entities.isEmpty()) {
            List<Double> closePrices = entities.stream()
                    .map(e -> e.getClose().doubleValue())
                    .toList();
            data.setPrices(closePrices);
            data.setCurrentPrice(closePrices.getLast());
        }
        return data;
    }
}
