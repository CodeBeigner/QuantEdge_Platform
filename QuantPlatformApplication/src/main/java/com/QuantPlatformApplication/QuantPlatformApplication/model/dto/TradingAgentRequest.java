package com.QuantPlatformApplication.QuantPlatformApplication.model.dto;

import com.QuantPlatformApplication.QuantPlatformApplication.model.entity.AgentRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * Inbound DTO for creating/updating a trading agent.
 */
@Getter
@Setter
public class TradingAgentRequest {

    @NotBlank(message = "Agent name is required")
    private String name;

    @NotNull(message = "Strategy ID is required")
    private Long strategyId;

    private String cronExpression = "0 0 9 * * MON-FRI";

    /** The AI role for this agent. Defaults to QUANT_RESEARCHER if not specified. */
    private AgentRole agentRole;

    /** Optional custom system prompt override. If set, overrides the default role prompt. */
    private String customSystemPrompt;
}
