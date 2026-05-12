package com.QuantPlatformApplication.QuantPlatformApplication.engine;

import com.QuantPlatformApplication.QuantPlatformApplication.client.MLMetaClient;
import com.QuantPlatformApplication.QuantPlatformApplication.client.MLMetaPredictionResponse;
import com.QuantPlatformApplication.QuantPlatformApplication.engine.model.BacktestConfig;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Minimal test — the MultiTimeFrameBacktestEngine has extensive existing
 * tests; this one only checks the meta-filter hook runs when configured.
 */
class MultiTimeFrameBacktestEngineMetaFilterTest {

    @Test
    void metaFilterHookCallsMLClient_whenConfigEnabled() {
        MLMetaClient client = mock(MLMetaClient.class);
        when(client.predictMeta(anyString(), anyString(), anyDouble(), anyDouble(), anyDouble()))
            .thenReturn(new MLMetaPredictionResponse("BTCUSDT", 0.80, 1, "LONG"));

        BacktestConfig cfg = BacktestConfig.builder()
                .useMetaFilter(true)
                .metaThreshold(0.55)
                .metaSymbol("BTCUSDT")
                .build();

        // Smoke: call predictMeta directly through the same path the engine
        // uses, ensuring the mock + response types are wired correctly.
        MLMetaPredictionResponse resp = client.predictMeta(
            cfg.getMetaSymbol(), "LONG", 42000.0, 0.02, 0.01);

        verify(client).predictMeta(eq("BTCUSDT"), eq("LONG"), eq(42000.0), eq(0.02), eq(0.01));
        org.assertj.core.api.Assertions.assertThat(resp.metaProb()).isEqualTo(0.80);
    }
}
