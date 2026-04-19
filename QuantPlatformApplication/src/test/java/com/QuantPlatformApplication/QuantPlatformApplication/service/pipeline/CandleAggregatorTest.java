package com.QuantPlatformApplication.QuantPlatformApplication.service.pipeline;

import com.QuantPlatformApplication.QuantPlatformApplication.engine.model.Candle;
import com.QuantPlatformApplication.QuantPlatformApplication.engine.model.TimeFrame;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CandleAggregatorTest {

    private final CandleAggregator aggregator = new CandleAggregator();

    @Test
    void aggregateFour15mCandlesIntoOne1hCandle() {
        Instant base = Instant.parse("2026-04-18T00:00:00Z");
        List<Candle> m15Candles = List.of(
            new Candle(base, 100, 105, 98, 103, 1000, TimeFrame.M15),
            new Candle(base.plusSeconds(900), 103, 110, 102, 108, 1200, TimeFrame.M15),
            new Candle(base.plusSeconds(1800), 108, 112, 106, 107, 800, TimeFrame.M15),
            new Candle(base.plusSeconds(2700), 107, 109, 104, 106, 900, TimeFrame.M15)
        );

        List<Candle> h1Candles = aggregator.aggregate(m15Candles, TimeFrame.M15, TimeFrame.H1);

        assertEquals(1, h1Candles.size());
        Candle h1 = h1Candles.get(0);
        assertEquals(100, h1.open());
        assertEquals(112, h1.high());
        assertEquals(98, h1.low());
        assertEquals(106, h1.close());
        assertEquals(3900, h1.volume());
        assertEquals(TimeFrame.H1, h1.timeFrame());
    }

    @Test
    void aggregateSixteen15mCandlesIntoOne4hCandle() {
        Instant base = Instant.parse("2026-04-18T00:00:00Z");
        List<Candle> m15Candles = new ArrayList<>();
        for (int i = 0; i < 16; i++) {
            m15Candles.add(new Candle(
                base.plusSeconds(i * 900L),
                100 + i, 105 + i, 98 + i, 102 + i, 100, TimeFrame.M15
            ));
        }

        List<Candle> h4Candles = aggregator.aggregate(m15Candles, TimeFrame.M15, TimeFrame.H4);

        assertEquals(1, h4Candles.size());
        Candle h4 = h4Candles.get(0);
        assertEquals(100, h4.open());
        assertEquals(120, h4.high());
        assertEquals(98, h4.low());
        assertEquals(117, h4.close());
        assertEquals(1600, h4.volume());
        assertEquals(TimeFrame.H4, h4.timeFrame());
    }

    @Test
    void incompleteGroupIsIgnored() {
        Instant base = Instant.parse("2026-04-18T00:00:00Z");
        List<Candle> m15Candles = List.of(
            new Candle(base, 100, 105, 98, 103, 1000, TimeFrame.M15),
            new Candle(base.plusSeconds(900), 103, 110, 102, 108, 1200, TimeFrame.M15)
        );

        List<Candle> h1Candles = aggregator.aggregate(m15Candles, TimeFrame.M15, TimeFrame.H1);

        assertEquals(0, h1Candles.size());
    }

    @Test
    void multipleCompleteGroupsProduceMultipleCandles() {
        Instant base = Instant.parse("2026-04-18T00:00:00Z");
        List<Candle> m15Candles = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            m15Candles.add(new Candle(
                base.plusSeconds(i * 900L),
                100 + i, 105 + i, 98 + i, 102 + i, 100, TimeFrame.M15
            ));
        }

        List<Candle> h1Candles = aggregator.aggregate(m15Candles, TimeFrame.M15, TimeFrame.H1);

        assertEquals(2, h1Candles.size());
    }

    @Test
    void emptyInputReturnsEmptyList() {
        List<Candle> result = aggregator.aggregate(List.of(), TimeFrame.M15, TimeFrame.H1);
        assertTrue(result.isEmpty());
    }
}
