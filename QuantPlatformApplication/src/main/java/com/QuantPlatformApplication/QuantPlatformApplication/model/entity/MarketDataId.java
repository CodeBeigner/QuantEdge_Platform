package com.QuantPlatformApplication.QuantPlatformApplication.model.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/**
 * Composite primary key for MarketDataEntity.
 * The market_data table uses (symbol, time) as its primary key.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MarketDataId implements Serializable {

    private String symbol;
    private Instant time;

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        MarketDataId that = (MarketDataId) o;
        return Objects.equals(symbol, that.symbol) && Objects.equals(time, that.time);
    }

    @Override
    public int hashCode() {
        return Objects.hash(symbol, time);
    }
}
