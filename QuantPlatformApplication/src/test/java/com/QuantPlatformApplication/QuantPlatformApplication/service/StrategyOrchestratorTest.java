package com.QuantPlatformApplication.QuantPlatformApplication.service;

import com.QuantPlatformApplication.QuantPlatformApplication.engine.model.*;
import com.QuantPlatformApplication.QuantPlatformApplication.engine.strategy.MultiTimeFrameStrategy;
import com.QuantPlatformApplication.QuantPlatformApplication.service.risk.TradeRiskEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class StrategyOrchestratorTest {

    @Mock private TradeRiskEngine riskEngine;
    @Mock private MultiTimeFrameStrategy mockStrategy;
    @Mock private ExecutionModeRouter executionRouter;

    private StrategyOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        orchestrator = new StrategyOrchestrator(
            List.of(mockStrategy), riskEngine, executionRouter
        );
    }

    private MultiTimeFrameData buildData() {
        IndicatorSnapshot snap = new IndicatorSnapshot(TimeFrame.M15,
            67000, 67010, 1.0, 55, 67200, 67000, 66800, 0.03, 0.5,
            30, 67000, 25, 1.5, 1.0, 0.5, 0.3);
        return MultiTimeFrameData.builder()
            .symbol("BTCUSD").currentPrice(67000)
            .indicators15m(snap).indicators1h(snap).indicators4h(snap)
            .fundingRate(0.01).fundingRateHistory(List.of(0.01))
            .build();
    }

    private TradeSignal buildSignal() {
        return TradeSignal.builder()
            .symbol("BTCUSD").action(Action.BUY)
            .entryPrice(67000).stopLossPrice(66650).takeProfitPrice(67525)
            .confidence(0.8).strategyName("TEST")
            .biasExplanation("test").triggerExplanation("test")
            .lesson("test").metadata(Map.of())
            .build();
    }

    @Test
    void strategySignalPassesRiskEngineAndExecutes() {
        TradeSignal signal = buildSignal();
        when(mockStrategy.analyze(any())).thenReturn(Optional.of(signal));
        when(riskEngine.evaluate(any(), anyDouble(), anyDouble(), anyDouble(), anyDouble(), any(), any()))
            .thenReturn(RiskCheckResult.approve(0.015, 5.0, 2.2, 10));

        orchestrator.evaluateStrategies(buildData(), 500, 500, 0, 0, Set.of(),
            RiskParameters.builder().build(), "AUTONOMOUS");

        verify(riskEngine).evaluate(any(), eq(500.0), eq(500.0), eq(0.0), eq(0.0), eq(Set.of()), any());
        verify(executionRouter).route(any(), any(), eq("AUTONOMOUS"));
    }

    @Test
    void rejectedTradeIsNotExecuted() {
        TradeSignal signal = buildSignal();
        when(mockStrategy.analyze(any())).thenReturn(Optional.of(signal));
        when(riskEngine.evaluate(any(), anyDouble(), anyDouble(), anyDouble(), anyDouble(), any(), any()))
            .thenReturn(RiskCheckResult.reject(List.of("leverage exceeded")));

        orchestrator.evaluateStrategies(buildData(), 500, 500, 0, 0, Set.of(),
            RiskParameters.builder().build(), "AUTONOMOUS");

        verify(executionRouter, never()).route(any(), any(), anyString());
    }

    @Test
    void noSignalMeansNoRiskCheck() {
        when(mockStrategy.analyze(any())).thenReturn(Optional.empty());

        orchestrator.evaluateStrategies(buildData(), 500, 500, 0, 0, Set.of(),
            RiskParameters.builder().build(), "AUTONOMOUS");

        verify(riskEngine, never()).evaluate(any(), anyDouble(), anyDouble(), anyDouble(), anyDouble(), any(), any());
        verify(executionRouter, never()).route(any(), any(), anyString());
    }

    @Test
    void humanInLoopModePassedToRouter() {
        TradeSignal signal = buildSignal();
        when(mockStrategy.analyze(any())).thenReturn(Optional.of(signal));
        when(riskEngine.evaluate(any(), anyDouble(), anyDouble(), anyDouble(), anyDouble(), any(), any()))
            .thenReturn(RiskCheckResult.approve(0.015, 5.0, 2.2, 10));

        orchestrator.evaluateStrategies(buildData(), 500, 500, 0, 0, Set.of(),
            RiskParameters.builder().build(), "HUMAN_IN_LOOP");

        verify(executionRouter).route(any(), any(), eq("HUMAN_IN_LOOP"));
    }

    @Test
    void multipleStrategiesEvaluatedIndependently() {
        MultiTimeFrameStrategy strategy2 = mock(MultiTimeFrameStrategy.class);
        StrategyOrchestrator multiOrch = new StrategyOrchestrator(
            List.of(mockStrategy, strategy2), riskEngine, executionRouter
        );

        when(mockStrategy.analyze(any())).thenReturn(Optional.empty());
        when(strategy2.analyze(any())).thenReturn(Optional.of(buildSignal()));
        when(riskEngine.evaluate(any(), anyDouble(), anyDouble(), anyDouble(), anyDouble(), any(), any()))
            .thenReturn(RiskCheckResult.approve(0.015, 5.0, 2.2, 10));

        multiOrch.evaluateStrategies(buildData(), 500, 500, 0, 0, Set.of(),
            RiskParameters.builder().build(), "AUTONOMOUS");

        verify(mockStrategy).analyze(any());
        verify(strategy2).analyze(any());
        verify(executionRouter, times(1)).route(any(), any(), anyString());
    }
}
