package com.QuantPlatformApplication.QuantPlatformApplication.engine.model;

import lombok.Builder;
import lombok.Getter;

import java.util.Map;

@Getter
@Builder
public class TradeSignal {

    private final String symbol;
    private final Action action;
    private final double entryPrice;
    private final double stopLossPrice;
    private final double takeProfitPrice;
    private final double confidence;
    private final String strategyName;

    private final String biasExplanation;
    private final String zoneExplanation;
    private final String triggerExplanation;
    private final String fundingExplanation;
    private final String riskExplanation;
    private final String lesson;

    private final Map<String, Object> metadata;

    public TradeRequest toTradeRequest() {
        return TradeRequest.builder()
            .symbol(symbol)
            .action(action)
            .entryPrice(entryPrice)
            .stopLossPrice(stopLossPrice)
            .takeProfitPrice(takeProfitPrice)
            .confidence(confidence)
            .strategyName(strategyName)
            .reasoning(biasExplanation + " | " + triggerExplanation)
            .build();
    }
}
