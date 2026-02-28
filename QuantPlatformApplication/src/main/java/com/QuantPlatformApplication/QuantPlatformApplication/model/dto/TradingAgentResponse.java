package com.QuantPlatformApplication.QuantPlatformApplication.model.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Getter
@Builder
public class TradingAgentResponse {
    private Long id;
    private String name;
    private Long strategyId;
    private String cronExpression;
    private Boolean active;
    private Instant lastRunAt;
    private Instant createdAt;
    private Instant updatedAt;
}
