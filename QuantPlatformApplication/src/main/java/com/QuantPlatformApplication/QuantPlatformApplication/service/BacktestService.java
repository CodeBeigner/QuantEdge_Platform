package com.QuantPlatformApplication.QuantPlatformApplication.service;

import com.QuantPlatformApplication.QuantPlatformApplication.engine.BacktestEngine;
import com.QuantPlatformApplication.QuantPlatformApplication.engine.model.ModelType;
import com.QuantPlatformApplication.QuantPlatformApplication.engine.model.StrategyConfig;
import com.QuantPlatformApplication.QuantPlatformApplication.engine.strategy.TradingStrategy;
import com.QuantPlatformApplication.QuantPlatformApplication.model.dto.BacktestRequest;
import com.QuantPlatformApplication.QuantPlatformApplication.model.dto.BacktestResponse;
import com.QuantPlatformApplication.QuantPlatformApplication.model.entity.BacktestResult;
import com.QuantPlatformApplication.QuantPlatformApplication.model.entity.MarketDataEntity;
import com.QuantPlatformApplication.QuantPlatformApplication.model.entity.Strategy;
import com.QuantPlatformApplication.QuantPlatformApplication.repository.BacktestResultRepository;
import com.QuantPlatformApplication.QuantPlatformApplication.repository.StrategyRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;

/**
 * Orchestrates backtesting: loads strategy + data, runs engine, persists results.
 *
 * <p>Includes walk-forward validation support (Phase 3).
 */
@Slf4j
@Service
public class BacktestService {

    private final StrategyRepository strategyRepository;
    private final MarketDataService marketDataService;
    private final BacktestResultRepository backtestResultRepository;
    private final Map<ModelType, TradingStrategy> strategyMap;
    private final ObjectMapper objectMapper;

    /** Default number of data points to fetch for walk-forward */
    private static final int WALK_FORWARD_DATA_DAYS = 756; // ~3 years

    /** Default train window for walk-forward (1 year of trading days) */
    private static final int DEFAULT_TRAIN_WINDOW = 252;

    /** Default test window for walk-forward (1 quarter of trading days) */
    private static final int DEFAULT_TEST_WINDOW = 63;

    public BacktestService(StrategyRepository strategyRepository,
            MarketDataService marketDataService,
            BacktestResultRepository backtestResultRepository,
            List<TradingStrategy> strategies,
            ObjectMapper objectMapper) {
        this.strategyRepository = strategyRepository;
        this.marketDataService = marketDataService;
        this.backtestResultRepository = backtestResultRepository;
        this.objectMapper = objectMapper;

        // Build strategy lookup by model type
        this.strategyMap = new HashMap<>();
        strategies.forEach(s -> strategyMap.put(s.getModelType(), s));
    }

    /**
     * Run a standard backtest for a strategy over a date range.
     *
     * @param request backtest parameters (strategyId, date range, initial capital)
     * @return backtest response with metrics and equity curve
     * @throws IllegalArgumentException if strategy not found or insufficient data
     */
    public BacktestResponse runBacktest(BacktestRequest request) {
        Strategy entity = strategyRepository.findById(request.getStrategyId())
                .orElseThrow(() -> new IllegalArgumentException("Strategy not found: " + request.getStrategyId()));

        TradingStrategy strategy = strategyMap.get(entity.getModelType());
        if (strategy == null) {
            throw new IllegalArgumentException("No strategy implementation for type: " + entity.getModelType());
        }

        // Fetch historical data for the date range
        Instant start = request.getStartDate().atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant end = request.getEndDate().atStartOfDay(ZoneOffset.UTC).toInstant();
        List<MarketDataEntity> dataEntities = marketDataService.fetchDailyData(entity.getSymbol(), start, end);

        if (dataEntities.size() < 50) {
            throw new IllegalArgumentException(
                    "Insufficient market data. Need at least 50 data points, got: " + dataEntities.size());
        }

        List<Double> prices = dataEntities.stream()
                .map(e -> e.getClose().doubleValue())
                .toList();

        // Build strategy config
        StrategyConfig config = new StrategyConfig();
        config.setId(entity.getId());
        config.setName(entity.getName());
        config.setModelType(entity.getModelType());
        config.setSymbol(entity.getSymbol());
        config.setCurrentCash(request.getInitialCapital());
        config.setPositionMultiplier(entity.getPositionMultiplier().doubleValue());
        config.setTargetRisk(entity.getTargetRisk().doubleValue());

        // Run backtest
        BacktestEngine.BacktestMetrics metrics = BacktestEngine.run(strategy, config, prices);

        // Persist result
        BacktestResult result = BacktestResult.builder()
                .strategyId(entity.getId())
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .initialCapital(BigDecimal.valueOf(metrics.getInitialCapital()))
                .finalCapital(BigDecimal.valueOf(metrics.getFinalCapital()))
                .totalReturn(BigDecimal.valueOf(metrics.getTotalReturn()))
                .sharpeRatio(BigDecimal.valueOf(metrics.getSharpeRatio()))
                .maxDrawdown(BigDecimal.valueOf(metrics.getMaxDrawdown()))
                .winRate(BigDecimal.valueOf(metrics.getWinRate()))
                .totalTrades(metrics.getTotalTrades())
                .equityCurve(serializeEquityCurve(metrics.getEquityCurve()))
                .transactionCosts(BigDecimal.valueOf(metrics.getTransactionCosts()))
                .build();

        BacktestResult saved = backtestResultRepository.save(result);
        log.info("Backtest complete for strategy {}: return={}, sharpe={}",
                entity.getName(), metrics.getTotalReturn() * 100, metrics.getSharpeRatio());

        return toResponse(saved, metrics.getEquityCurve());
    }

    /**
     * Get all backtest results for a strategy.
     *
     * @param strategyId the strategy ID
     * @return list of backtest responses
     */
    public List<BacktestResponse> getBacktestsByStrategy(Long strategyId) {
        return backtestResultRepository.findByStrategyIdOrderByCreatedAtDesc(strategyId)
                .stream()
                .map(r -> toResponse(r, deserializeEquityCurve(r.getEquityCurve())))
                .toList();
    }

    /**
     * Get the most recent backtest result for a strategy.
     * Used by the AI agent pipeline to include backtest metrics in research context.
     *
     * @param strategyId the strategy ID
     * @return the latest BacktestResult, or empty if none exist
     */
    public Optional<BacktestResult> getLastBacktestForStrategy(Long strategyId) {
        return backtestResultRepository.findTopByStrategyIdOrderByCreatedAtDesc(strategyId);
    }

    /**
     * Run walk-forward validation for a strategy.
     *
     * <p>Fetches ~3 years of data and runs the walk-forward engine with
     * 1-year train / 1-quarter test windows.
     *
     * @param strategyId the strategy to validate
     * @return walk-forward result with per-window metrics and robustness verdict
     */
    public Map<String, Object> runWalkForwardBacktest(Long strategyId) {
        Strategy entity = strategyRepository.findById(strategyId)
                .orElseThrow(() -> new IllegalArgumentException("Strategy not found: " + strategyId));

        TradingStrategy strategy = strategyMap.get(entity.getModelType());
        if (strategy == null) {
            throw new IllegalArgumentException("No strategy implementation for type: " + entity.getModelType());
        }

        // Fetch historical data
        List<MarketDataEntity> dataEntities = marketDataService.fetchRecentData(
                entity.getSymbol(), WALK_FORWARD_DATA_DAYS);

        if (dataEntities.size() < DEFAULT_TRAIN_WINDOW + DEFAULT_TEST_WINDOW) {
            throw new IllegalArgumentException(
                    "Insufficient data for walk-forward. Need at least " +
                    (DEFAULT_TRAIN_WINDOW + DEFAULT_TEST_WINDOW) + " points, got: " + dataEntities.size());
        }

        List<Double> prices = dataEntities.stream()
                .map(e -> e.getClose().doubleValue())
                .toList();

        StrategyConfig config = new StrategyConfig();
        config.setId(entity.getId());
        config.setName(entity.getName());
        config.setModelType(entity.getModelType());
        config.setSymbol(entity.getSymbol());
        config.setCurrentCash(entity.getCurrentCash().doubleValue());
        config.setPositionMultiplier(entity.getPositionMultiplier().doubleValue());
        config.setTargetRisk(entity.getTargetRisk().doubleValue());

        BacktestEngine.WalkForwardResult wfResult = BacktestEngine.runWalkForward(
                strategy, config, prices, DEFAULT_TRAIN_WINDOW, DEFAULT_TEST_WINDOW);

        // Persist the walk-forward validated backtest result
        if (!wfResult.getWindows().isEmpty()) {
            // Use last window metrics as the representative result
            BacktestEngine.BacktestMetrics lastWindow = wfResult.getWindows().getLast();
            BacktestResult result = BacktestResult.builder()
                    .strategyId(entity.getId())
                    .startDate(java.time.LocalDate.now().minusDays(WALK_FORWARD_DATA_DAYS))
                    .endDate(java.time.LocalDate.now())
                    .initialCapital(BigDecimal.valueOf(lastWindow.getInitialCapital()))
                    .finalCapital(BigDecimal.valueOf(lastWindow.getFinalCapital()))
                    .totalReturn(BigDecimal.valueOf(lastWindow.getTotalReturn()))
                    .sharpeRatio(BigDecimal.valueOf(wfResult.getMeanSharpe()))
                    .maxDrawdown(BigDecimal.valueOf(wfResult.getWorstDrawdown()))
                    .winRate(BigDecimal.valueOf(lastWindow.getWinRate()))
                    .totalTrades(lastWindow.getTotalTrades())
                    .equityCurve(serializeEquityCurve(lastWindow.getEquityCurve()))
                    .transactionCosts(BigDecimal.valueOf(lastWindow.getTransactionCosts()))
                    .isWalkForwardValidated(true)
                    .walkForwardWindows(wfResult.getWindowCount())
                    .walkForwardMeanSharpe(BigDecimal.valueOf(wfResult.getMeanSharpe()))
                    .walkForwardStdSharpe(BigDecimal.valueOf(wfResult.getStdDevSharpe()))
                    .build();
            backtestResultRepository.save(result);
        }

        // Return results
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("strategyId", strategyId);
        response.put("strategyName", entity.getName());
        response.put("meanSharpe", wfResult.getMeanSharpe());
        response.put("stdDevSharpe", wfResult.getStdDevSharpe());
        response.put("meanDrawdown", wfResult.getMeanDrawdown());
        response.put("worstDrawdown", wfResult.getWorstDrawdown());
        response.put("isRobust", wfResult.isRobust());
        response.put("windowCount", wfResult.getWindowCount());
        response.put("windows", wfResult.getWindows().stream().map(w -> Map.of(
                "sharpeRatio", w.getSharpeRatio(),
                "maxDrawdown", w.getMaxDrawdown(),
                "totalReturn", w.getTotalReturn(),
                "totalTrades", w.getTotalTrades(),
                "winRate", w.getWinRate(),
                "transactionCosts", w.getTransactionCosts()
        )).toList());

        log.info("Walk-forward complete for strategy {}: meanSharpe={}, robust={}",
                entity.getName(), wfResult.getMeanSharpe(), wfResult.isRobust());

        return response;
    }

    private BacktestResponse toResponse(BacktestResult r, List<Double> equityCurve) {
        return BacktestResponse.builder()
                .id(r.getId())
                .strategyId(r.getStrategyId())
                .startDate(r.getStartDate())
                .endDate(r.getEndDate())
                .initialCapital(r.getInitialCapital())
                .finalCapital(r.getFinalCapital())
                .totalReturn(r.getTotalReturn())
                .sharpeRatio(r.getSharpeRatio())
                .maxDrawdown(r.getMaxDrawdown())
                .winRate(r.getWinRate())
                .totalTrades(r.getTotalTrades())
                .equityCurve(equityCurve)
                .createdAt(r.getCreatedAt())
                .build();
    }

    private String serializeEquityCurve(List<Double> curve) {
        try {
            return objectMapper.writeValueAsString(curve);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }

    @SuppressWarnings("unchecked")
    private List<Double> deserializeEquityCurve(String json) {
        try {
            if (json == null || json.isEmpty())
                return List.of();
            return objectMapper.readValue(json, List.class);
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }
}
