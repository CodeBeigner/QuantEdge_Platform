package com.QuantPlatformApplication.QuantPlatformApplication.event;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.Map;

/**
 * Event published when a strategy is executed (live or via agent).
 */
@Getter
@Builder
public class StrategyExecutionEvent {
    private Long strategyId;
    private String strategyName;
    private String modelType;
    private String action;
    private int quantity;
    private double price;
    private double confidence;
    private String reasoning;
    private boolean success;
    private String error;
    private Instant executedAt;
}
