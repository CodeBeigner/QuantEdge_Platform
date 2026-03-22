package com.QuantPlatformApplication.QuantPlatformApplication.service;

import com.QuantPlatformApplication.QuantPlatformApplication.engine.StrategyExecutor;
import com.QuantPlatformApplication.QuantPlatformApplication.engine.model.Action;
import com.QuantPlatformApplication.QuantPlatformApplication.engine.model.ExecutionResult;
import com.QuantPlatformApplication.QuantPlatformApplication.engine.model.MarketData;
import com.QuantPlatformApplication.QuantPlatformApplication.engine.model.StrategyConfig;
import com.QuantPlatformApplication.QuantPlatformApplication.model.dto.ExecutionResponse;
import com.QuantPlatformApplication.QuantPlatformApplication.model.dto.StrategyRequest;
import com.QuantPlatformApplication.QuantPlatformApplication.model.dto.StrategyResponse;
import com.QuantPlatformApplication.QuantPlatformApplication.model.entity.MarketDataEntity;
import com.QuantPlatformApplication.QuantPlatformApplication.model.entity.Order;
import com.QuantPlatformApplication.QuantPlatformApplication.model.entity.Strategy;
import com.QuantPlatformApplication.QuantPlatformApplication.repository.StrategyRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import com.QuantPlatformApplication.QuantPlatformApplication.engine.model.ModelType;
import io.micrometer.core.annotation.Timed;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Service layer for strategy CRUD and execution.
 * 
 * This is the bridge between:
 * - The REST controller (StrategyController)
 * - The JPA persistence layer (StrategyRepository / Strategy entity)
 * - The execution engine (StrategyExecutor / StrategyConfig)
 * - The market data pipeline (MarketDataService / MarketDataEntity)
 * - The order management layer (OrderManagementService)
 * 
 * Key responsibility: convert between DB entities and engine domain models
 * so each layer stays decoupled.
 *
 * BUG 2 FIX: Strategy decisions (BUY/SELL with confidence >= threshold) are
 * now automatically converted to orders via OrderManagementService.
 */
@Slf4j
@Service
public class StrategyService {

    private final StrategyRepository strategyRepository;
    private final StrategyExecutor strategyExecutor;
    private final MarketDataService marketDataService;
    private final MacroDataService macroDataService;
    private final RegimeDetectionService regimeDetectionService;
    private final OrderManagementService orderManagementService;

    /** How many trading days of data to load for strategy execution */
    private static final int DEFAULT_LOOKBACK_DAYS = 252;

    /** Minimum confidence threshold to auto-place an order from a strategy decision */
    private static final double MINIMUM_CONFIDENCE_THRESHOLD = 60.0;

    public StrategyService(StrategyRepository strategyRepository,
            StrategyExecutor strategyExecutor,
            MarketDataService marketDataService,
            MacroDataService macroDataService,
            RegimeDetectionService regimeDetectionService,
            OrderManagementService orderManagementService) {
        this.strategyRepository = strategyRepository;
        this.strategyExecutor = strategyExecutor;
        this.marketDataService = marketDataService;
        this.macroDataService = macroDataService;
        this.regimeDetectionService = regimeDetectionService;
        this.orderManagementService = orderManagementService;
    }

    // ========================================================================
    // CRUD
    // ========================================================================

    /**
     * Create a new strategy from the given request.
     *
     * @param request strategy creation parameters
     * @return the created strategy as a response DTO
     */
    public StrategyResponse createStrategy(StrategyRequest request) {
        Strategy entity = Strategy.builder()
                .name(request.getName())
                .symbol(request.getSymbol().toUpperCase())
                .modelType(request.getModelType())
                .currentCash(BigDecimal.valueOf(request.getCurrentCash()))
                .positionMultiplier(BigDecimal.valueOf(request.getPositionMultiplier()))
                .targetRisk(BigDecimal.valueOf(request.getTargetRisk()))
                .build();

        Strategy saved = strategyRepository.save(entity);
        log.info("Created strategy: id={}, name={}, type={}", saved.getId(), saved.getName(), saved.getModelType());
        return toResponse(saved);
    }

    /**
     * Get a strategy by its ID.
     *
     * @param id strategy ID
     * @return strategy response DTO
     * @throws IllegalArgumentException if not found
     */
    public StrategyResponse getStrategy(Long id) {
        Strategy entity = findOrThrow(id);
        return toResponse(entity);
    }

    /**
     * List all strategies.
     *
     * @return list of all strategies as response DTOs
     */
    public List<StrategyResponse> getAllStrategies() {
        return strategyRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Update a strategy with the given request parameters.
     *
     * @param id      strategy ID to update
     * @param request new strategy parameters
     * @return updated strategy response DTO
     * @throws IllegalArgumentException if not found
     */
    public StrategyResponse updateStrategy(Long id, StrategyRequest request) {
        Strategy entity = findOrThrow(id);

        entity.setName(request.getName());
        entity.setSymbol(request.getSymbol().toUpperCase());
        entity.setModelType(request.getModelType());
        entity.setCurrentCash(BigDecimal.valueOf(request.getCurrentCash()));
        entity.setPositionMultiplier(BigDecimal.valueOf(request.getPositionMultiplier()));
        entity.setTargetRisk(BigDecimal.valueOf(request.getTargetRisk()));
        entity.setUpdatedAt(Instant.now());

        Strategy saved = strategyRepository.save(entity);
        log.info("Updated strategy: id={}", saved.getId());
        return toResponse(saved);
    }

    /**
     * Delete a strategy by its ID.
     *
     * @param id strategy ID
     * @throws IllegalArgumentException if not found
     */
    public void deleteStrategy(Long id) {
        Strategy entity = findOrThrow(id);
        strategyRepository.delete(entity);
        log.info("Deleted strategy: id={}", id);
    }

    // ========================================================================
    // EXECUTION
    // ========================================================================

    /**
     * Execute a strategy against real market data from the database.
     * 
     * <p>Flow:
     * <ol>
     *   <li>Load strategy entity from DB</li>
     *   <li>Fetch recent price data via MarketDataService</li>
     *   <li>Convert DB entities → engine MarketData</li>
     *   <li>Convert Strategy entity → engine StrategyConfig</li>
     *   <li>Call StrategyExecutor.executeAsync() (runs on virtual thread)</li>
     *   <li>Map result → ExecutionResponse DTO</li>
     *   <li>(BUG 2 FIX) If BUY/SELL with confidence >= threshold → place order</li>
     * </ol>
     *
     * @param id strategy ID to execute
     * @return execution response DTO, including placed order ID if applicable
     */
    public ExecutionResponse executeStrategy(Long id) {
        Strategy entity = findOrThrow(id);

        // 1. Load market data from DB
        List<MarketDataEntity> priceEntities = marketDataService.fetchRecentData(entity.getSymbol(),
                DEFAULT_LOOKBACK_DAYS);

        if (priceEntities.isEmpty()) {
            return ExecutionResponse.builder()
                    .strategyId(entity.getId())
                    .strategyName(entity.getName())
                    .success(false)
                    .error("No market data found for symbol: " + entity.getSymbol())
                    .executedAt(Instant.now())
                    .build();
        }

        // 2. Convert DB entities → engine MarketData + enrich with macro data
        MarketData marketData = toMarketData(priceEntities);
        enrichMarketData(marketData, entity);

        // 3. Convert Strategy entity → engine StrategyConfig
        StrategyConfig config = toConfig(entity);

        // 4. Execute (async via virtual threads, but we .join() for the API response)
        try {
            CompletableFuture<ExecutionResult> future = strategyExecutor.executeAsync(config, marketData);
            ExecutionResult result = future.join();

            ExecutionResponse response = toExecutionResponse(entity, result);

            // 5. BUG 2 FIX: Convert Decision → Order when conditions are met
            if (result.isSuccess() && result.getDecision() != null) {
                Action action = result.getDecision().action();
                double confidence = result.getDecision().confidence();

                if (action != Action.HOLD && confidence >= MINIMUM_CONFIDENCE_THRESHOLD) {
                    try {
                        Order placedOrder = orderManagementService.placeOrder(
                                entity.getSymbol(),
                                action.name(),
                                "MARKET",
                                result.getDecision().quantity(),
                                null,
                                entity.getId());
                        response.setOrderId(placedOrder.getId());
                        log.info("Strategy {} placed order: orderId={}, action={}, qty={}, confidence={}",
                                id, placedOrder.getId(), action, result.getDecision().quantity(), confidence);
                    } catch (Exception e) {
                        log.error("Strategy {} failed to place order: {}", id, e.getMessage(), e);
                    }
                } else {
                    log.info("Strategy {} decision not eligible for order: action={}, confidence={}",
                            id, action, confidence);
                }
            }

            return response;
        } catch (Exception e) {
            log.error("Strategy execution failed for id={}: {}", id, e.getMessage(), e);
            return ExecutionResponse.builder()
                    .strategyId(entity.getId())
                    .strategyName(entity.getName())
                    .success(false)
                    .error("Execution failed: " + e.getMessage())
                    .executedAt(Instant.now())
                    .build();
        }
    }

    // ========================================================================
    // CONVERTERS
    // ========================================================================

    /**
     * Convert a list of MarketDataEntity (DB rows) → engine MarketData.
     * The engine wants a list of close prices and a current price.
     */
    private MarketData toMarketData(List<MarketDataEntity> entities) {
        MarketData data = new MarketData();

        List<Double> closePrices = entities.stream()
                .map(e -> e.getClose().doubleValue())
                .toList();

        data.setPrices(closePrices);
        data.setCurrentPrice(closePrices.getLast()); // most recent close

        return data;
    }

    /**
     * Enrich MarketData with macro indicators based on strategy type.
     * MACRO and REGIME strategies need interest rates, inflation, VIX, yield curve.
     * CORRELATION strategy needs secondary symbol prices.
     */
    private void enrichMarketData(MarketData data, Strategy entity) {
        ModelType type = entity.getModelType();

        if (type == ModelType.MACRO || type == ModelType.REGIME) {
            data.setInterestRate(macroDataService.getInterestRate());
            data.setInflationRate(regimeDetectionService.getInflationRate());
            data.setVix(regimeDetectionService.getCurrentVix());
            data.setYieldCurveSpread(regimeDetectionService.getYieldCurveSpread());
        }

        if (type == ModelType.CORRELATION) {
            // Load secondary symbol (default: SPY as benchmark)
            String secondarySymbol = "SPY";
            if (!entity.getSymbol().equalsIgnoreCase(secondarySymbol)) {
                List<MarketDataEntity> secondary = marketDataService.fetchRecentData(
                        secondarySymbol, DEFAULT_LOOKBACK_DAYS);
                if (!secondary.isEmpty()) {
                    data.setSecondaryPrices(secondary.stream()
                            .map(e -> e.getClose().doubleValue())
                            .toList());
                }
            }
        }
    }

    /** Convert Strategy entity → engine StrategyConfig */
    private StrategyConfig toConfig(Strategy entity) {
        StrategyConfig config = new StrategyConfig();
        config.setId(entity.getId());
        config.setName(entity.getName());
        config.setModelType(entity.getModelType());
        config.setSymbol(entity.getSymbol());
        config.setCurrentCash(entity.getCurrentCash().doubleValue());
        config.setPositionMultiplier(entity.getPositionMultiplier().doubleValue());
        config.setTargetRisk(entity.getTargetRisk().doubleValue());
        return config;
    }

    /** Convert Strategy entity → StrategyResponse DTO */
    private StrategyResponse toResponse(Strategy entity) {
        return StrategyResponse.builder()
                .id(entity.getId())
                .name(entity.getName())
                .symbol(entity.getSymbol())
                .modelType(entity.getModelType())
                .currentCash(entity.getCurrentCash())
                .positionMultiplier(entity.getPositionMultiplier())
                .targetRisk(entity.getTargetRisk())
                .active(entity.getActive())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    /** Convert engine ExecutionResult + entity → ExecutionResponse DTO */
    private ExecutionResponse toExecutionResponse(Strategy entity, ExecutionResult result) {
        ExecutionResponse.ExecutionResponseBuilder builder = ExecutionResponse.builder()
                .strategyId(entity.getId())
                .strategyName(entity.getName())
                .success(result.isSuccess())
                .executedAt(Instant.now());

        if (result.isSuccess() && result.getDecision() != null) {
            builder.action(result.getDecision().action())
                    .quantity(result.getDecision().quantity())
                    .price(result.getDecision().price())
                    .reasoning(result.getDecision().reasoning())
                    .confidence(result.getDecision().confidence())
                    .metadata(result.getDecision().metadata());
        } else {
            builder.error(result.getError());
        }

        return builder.build();
    }

    /** Find a strategy or throw a clear error */
    private Strategy findOrThrow(Long id) {
        return strategyRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Strategy not found: id=" + id));
    }
}
