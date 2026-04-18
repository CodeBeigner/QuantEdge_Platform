package com.QuantPlatformApplication.QuantPlatformApplication.engine.model;

public record TradeExplanation(
    String bias,
    String zone,
    String entryTrigger,
    String fundingContext,
    String riskCalc,
    String lesson
) {}
