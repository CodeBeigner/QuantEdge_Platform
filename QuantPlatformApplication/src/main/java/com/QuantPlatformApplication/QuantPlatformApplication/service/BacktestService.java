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
import java.util.List;
import java.util.Map;

/**
 * Orchestrates backtesting: loads strategy + data, runs engine, persists
 * results.
 */
@Slf4j
@Service
public class BacktestService {

    private final StrategyRepository strategyRepository;
    private final MarketDataService marketDataService;
    private final BacktestResultRepository backtestResultRepository;
    private final Map<ModelType, TradingStrategy> strategyMap;
    private final ObjectMapper objectMapper;

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
        this.strategyMap = new java.util.HashMap<>();
        strategies.forEach(s -> strategyMap.put(s.getModelType(), s));
    }

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
                .build();

        BacktestResult saved = backtestResultRepository.save(result);
        log.info("Backtest complete for strategy {}: return={:.2f}%, sharpe={:.2f}",
                entity.getName(), metrics.getTotalReturn() * 100, metrics.getSharpeRatio());

        return toResponse(saved, metrics.getEquityCurve());
    }

    public List<BacktestResponse> getBacktestsByStrategy(Long strategyId) {
        return backtestResultRepository.findByStrategyIdOrderByCreatedAtDesc(strategyId)
                .stream()
                .map(r -> toResponse(r, deserializeEquityCurve(r.getEquityCurve())))
                .toList();
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
