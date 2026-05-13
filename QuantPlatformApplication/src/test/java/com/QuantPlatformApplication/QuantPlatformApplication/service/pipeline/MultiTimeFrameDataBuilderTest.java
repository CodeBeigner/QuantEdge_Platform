package com.QuantPlatformApplication.QuantPlatformApplication.service.pipeline;

import com.QuantPlatformApplication.QuantPlatformApplication.engine.data.CandleSource;
import com.QuantPlatformApplication.QuantPlatformApplication.engine.model.Candle;
import com.QuantPlatformApplication.QuantPlatformApplication.engine.model.IndicatorSnapshot;
import com.QuantPlatformApplication.QuantPlatformApplication.engine.model.MultiTimeFrameData;
import com.QuantPlatformApplication.QuantPlatformApplication.engine.model.TimeFrame;
import com.QuantPlatformApplication.QuantPlatformApplication.service.pipeline.IndicatorCalculator;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MultiTimeFrameDataBuilderTest {

    private Candle bar(long epochSec, double close) {
        return new Candle(Instant.ofEpochSecond(epochSec), close, close + 1, close - 1, close, 100.0, TimeFrame.M15);
    }

    @Test
    void build_populatesAllThreeTimeframes() {
        CandleSource source = mock(CandleSource.class);
        IndicatorCalculator calc = mock(IndicatorCalculator.class);

        List<Candle> candles15 = List.of(bar(1_700_000_000L, 100), bar(1_700_000_900L, 101));
        List<Candle> candles1h = List.of(bar(1_700_000_000L, 100));
        List<Candle> candles4h = List.of(bar(1_700_000_000L, 100));

        when(source.fetch(eq("BTCUSDT"), eq("15m"), any(LocalDate.class), any(LocalDate.class))).thenReturn(candles15);
        when(source.fetch(eq("BTCUSDT"), eq("1h"), any(LocalDate.class), any(LocalDate.class))).thenReturn(candles1h);
        when(source.fetch(eq("BTCUSDT"), eq("4h"), any(LocalDate.class), any(LocalDate.class))).thenReturn(candles4h);
        when(calc.calculate(any(), any())).thenReturn(new IndicatorSnapshot(TimeFrame.M15, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0));

        MultiTimeFrameDataBuilder builder = new MultiTimeFrameDataBuilder(source, calc);
        MultiTimeFrameData data = builder.build("BTCUSDT", LocalDate.of(2024, 1, 15));

        assertThat(data.getSymbol()).isEqualTo("BTCUSDT");
        assertThat(data.getCandles15m()).hasSize(2);
        assertThat(data.getCandles1h()).hasSize(1);
        assertThat(data.getCandles4h()).hasSize(1);
        assertThat(data.getCurrentPrice()).isEqualTo(101.0); // last 15m close
        assertThat(data.getIndicators15m()).isNotNull();
    }

    @Test
    void build_usesLastCloseForCurrentPrice() {
        CandleSource source = mock(CandleSource.class);
        IndicatorCalculator calc = mock(IndicatorCalculator.class);
        when(source.fetch(any(), any(), any(), any())).thenReturn(
            List.of(bar(1_700_000_000L, 100), bar(1_700_000_900L, 42000.5))
        );
        when(calc.calculate(any(), any())).thenReturn(new IndicatorSnapshot(TimeFrame.M15, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0));

        MultiTimeFrameData data = new MultiTimeFrameDataBuilder(source, calc)
            .build("BTCUSDT", LocalDate.of(2024, 1, 15));

        assertThat(data.getCurrentPrice()).isEqualTo(42000.5);
    }
}
