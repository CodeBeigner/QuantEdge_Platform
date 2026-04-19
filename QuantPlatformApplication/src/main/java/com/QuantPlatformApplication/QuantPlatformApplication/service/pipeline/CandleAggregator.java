package com.QuantPlatformApplication.QuantPlatformApplication.service.pipeline;

import com.QuantPlatformApplication.QuantPlatformApplication.engine.model.Candle;
import com.QuantPlatformApplication.QuantPlatformApplication.engine.model.TimeFrame;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class CandleAggregator {

    public List<Candle> aggregate(List<Candle> sourceCandles, TimeFrame source, TimeFrame target) {
        if (sourceCandles.isEmpty()) {
            return List.of();
        }

        int groupSize = target.getMultiplierFrom(source);
        List<Candle> result = new ArrayList<>();

        for (int i = 0; i + groupSize <= sourceCandles.size(); i += groupSize) {
            List<Candle> group = sourceCandles.subList(i, i + groupSize);
            result.add(mergeCandles(group, target));
        }

        return result;
    }

    private Candle mergeCandles(List<Candle> group, TimeFrame targetTf) {
        double open = group.get(0).open();
        double close = group.get(group.size() - 1).close();
        double high = Double.MIN_VALUE;
        double low = Double.MAX_VALUE;
        double volume = 0;

        for (Candle c : group) {
            high = Math.max(high, c.high());
            low = Math.min(low, c.low());
            volume += c.volume();
        }

        return new Candle(group.get(0).timestamp(), open, high, low, close, volume, targetTf);
    }
}
