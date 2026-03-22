package com.QuantPlatformApplication.QuantPlatformApplication.model.dto;

import com.QuantPlatformApplication.QuantPlatformApplication.engine.model.Action;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

/**
 * Outbound DTO for strategy execution results.
 * Wraps the engine's ExecutionResult/Decision into a clean API response.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExecutionResponse {

    private Long strategyId;
    private String strategyName;
    private boolean success;

    // Decision fields (null when success = false)
    private Action action;
    private int quantity;
    private double price;
    private String reasoning;
    private double confidence;
    private Map<String, Object> metadata;

    // Order placed (null when no order created)
    private Long orderId;

    // Error info (null when success = true)
    private String error;

    private Instant executedAt;
}
