package com.QuantPlatformApplication.QuantPlatformApplication.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TradingAgentRequest {

    @NotBlank(message = "Agent name is required")
    private String name;

    @NotNull(message = "Strategy ID is required")
    private Long strategyId;

    private String cronExpression = "0 0 9 * * MON-FRI";
}
