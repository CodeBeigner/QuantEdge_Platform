package com.QuantPlatformApplication.QuantPlatformApplication.service.pipeline;

import com.QuantPlatformApplication.QuantPlatformApplication.engine.model.MultiTimeFrameData;
import com.QuantPlatformApplication.QuantPlatformApplication.engine.model.RiskParameters;
import com.QuantPlatformApplication.QuantPlatformApplication.service.StrategyOrchestrator;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MarketTickSchedulerTest {

    @Test
    void runOnce_buildsDataAndEvaluatesForEachSymbol() {
        MultiTimeFrameDataBuilder builder = mock(MultiTimeFrameDataBuilder.class);
        StrategyOrchestrator orchestrator = mock(StrategyOrchestrator.class);
        MultiTimeFrameData data = MultiTimeFrameData.builder().symbol("BTCUSDT").currentPrice(42000).build();
        when(builder.build(anyString(), any())).thenReturn(data);

        MarketTickScheduler scheduler = new MarketTickScheduler(
            builder, orchestrator, List.of("BTCUSDT", "ETHUSDT"),
            500.0, 500.0, RiskParameters.builder().build(), "AUTONOMOUS");

        scheduler.runOnce();

        verify(builder).build(eq("BTCUSDT"), any());
        verify(builder).build(eq("ETHUSDT"), any());
        verify(orchestrator, times(2)).evaluateStrategies(
            any(), anyDouble(), anyDouble(), anyDouble(), anyDouble(), any(Set.class), any(), anyString());
    }

    @Test
    void runOnce_continuesOnPerSymbolFailure() {
        MultiTimeFrameDataBuilder builder = mock(MultiTimeFrameDataBuilder.class);
        StrategyOrchestrator orchestrator = mock(StrategyOrchestrator.class);
        when(builder.build(eq("BTCUSDT"), any())).thenThrow(new RuntimeException("db down"));
        when(builder.build(eq("ETHUSDT"), any())).thenReturn(
            MultiTimeFrameData.builder().symbol("ETHUSDT").currentPrice(2500).build());

        MarketTickScheduler scheduler = new MarketTickScheduler(
            builder, orchestrator, List.of("BTCUSDT", "ETHUSDT"),
            500.0, 500.0, RiskParameters.builder().build(), "AUTONOMOUS");

        scheduler.runOnce(); // must not throw

        verify(orchestrator, times(1)).evaluateStrategies(
            any(), anyDouble(), anyDouble(), anyDouble(), anyDouble(), any(Set.class), any(), anyString());
    }
}
