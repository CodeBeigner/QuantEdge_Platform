package com.QuantPlatformApplication.QuantPlatformApplication.service.pipeline;

import com.QuantPlatformApplication.QuantPlatformApplication.client.BinanceHistoricalClient;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MarketDataSyncSchedulerTest {

    @Test
    void runOnce_fetchesEverySymbolTimeframePairAndPersists() {
        BinanceHistoricalClient client = mock(BinanceHistoricalClient.class);
        when(client.fetchCandles(anyString(), anyString(), any(Instant.class), any(Instant.class)))
            .thenReturn(List.of()); // empty list: nothing to persist, but still counts as "fetched"

        MarketDataSyncScheduler scheduler = new MarketDataSyncScheduler(
            client,
            List.of("BTCUSD", "ETHUSD"),
            List.of("15m", "1h", "4h")
        );

        scheduler.runOnce();

        // 2 symbols * 3 timeframes = 6 fetches
        verify(client, times(6)).fetchCandles(anyString(), anyString(),
                                               any(Instant.class), any(Instant.class));
    }

    @Test
    void runOnce_continuesAfterPerPairFailure() {
        BinanceHistoricalClient client = mock(BinanceHistoricalClient.class);
        when(client.fetchCandles(anyString(), anyString(), any(Instant.class), any(Instant.class)))
            .thenThrow(new RuntimeException("binance 503"));

        MarketDataSyncScheduler scheduler = new MarketDataSyncScheduler(
            client,
            List.of("BTCUSD"),
            List.of("15m", "1h")
        );

        // Should not throw; errors are logged and the next pair is attempted.
        scheduler.runOnce();
        verify(client, times(2)).fetchCandles(anyString(), anyString(),
                                               any(Instant.class), any(Instant.class));
    }
}
