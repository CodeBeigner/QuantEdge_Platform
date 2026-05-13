package com.QuantPlatformApplication.QuantPlatformApplication.engine.data;

import com.QuantPlatformApplication.QuantPlatformApplication.engine.model.Candle;
import com.QuantPlatformApplication.QuantPlatformApplication.model.entity.MarketDataEntity;
import com.QuantPlatformApplication.QuantPlatformApplication.service.MarketDataService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MarketDataCandleSourceTest {

    private MarketDataEntity makeEntity(long epochMs, double close) {
        MarketDataEntity e = new MarketDataEntity();
        e.setTime(Instant.ofEpochMilli(epochMs));
        e.setSymbol("BTCUSDT");
        e.setTimeframe("15m");
        e.setOpen(BigDecimal.valueOf(close));
        e.setHigh(BigDecimal.valueOf(close + 1));
        e.setLow(BigDecimal.valueOf(close - 1));
        e.setClose(BigDecimal.valueOf(close));
        e.setVolume(BigDecimal.valueOf(100));
        return e;
    }

    @Test
    void fetch_returnsCandlesInChronologicalOrder() {
        MarketDataService svc = mock(MarketDataService.class);
        when(svc.fetchDailyData(anyString(), anyString(), any(), any()))
            .thenReturn(List.of(
                makeEntity(1_700_000_000_000L, 100.0),
                makeEntity(1_700_000_900_000L, 101.0),
                makeEntity(1_700_001_800_000L, 102.0)
            ));

        MarketDataCandleSource source = new MarketDataCandleSource(svc);
        List<Candle> candles = source.fetch("BTCUSDT", "15m", LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 2));

        assertThat(candles).hasSize(3);
        assertThat(candles.get(0).close()).isEqualTo(100.0);
        assertThat(candles.get(2).close()).isEqualTo(102.0);
    }

    @Test
    void fetch_throwsEmptyRangeExceptionWhenNoRows() {
        MarketDataService svc = mock(MarketDataService.class);
        when(svc.fetchDailyData(anyString(), anyString(), any(), any()))
            .thenReturn(List.of());

        MarketDataCandleSource source = new MarketDataCandleSource(svc);

        assertThatThrownBy(() -> source.fetch("NEWPAIR", "15m", LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 2)))
            .isInstanceOf(EmptyCandleRangeException.class)
            .hasMessageContaining("NEWPAIR")
            .hasMessageContaining("15m");
    }
}
