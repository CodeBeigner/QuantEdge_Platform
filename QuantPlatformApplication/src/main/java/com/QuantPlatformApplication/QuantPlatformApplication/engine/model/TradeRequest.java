package com.QuantPlatformApplication.QuantPlatformApplication.engine.model;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class TradeRequest {
    private final String symbol;
    private final Action action;
    private final double entryPrice;
    private final double stopLossPrice;
    private final double takeProfitPrice;
    private final double confidence;
    private final String strategyName;
    private final String reasoning;
}
