package com.QuantPlatformApplication.QuantPlatformApplication.model.dto;

import com.QuantPlatformApplication.QuantPlatformApplication.model.entity.AgentRole;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

/**
 * Outbound DTO for trading agent responses.
 */
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

    // ── AI Agent Layer fields ──
    private AgentRole agentRole;
    private String systemPromptPreview;  // first 200 chars of system prompt
    private String lastReasoning;
    private Double lastConfidence;
    private Integer totalExecutions;
    private Integer successfulExecutions;

    // ── Lifecycle fields ──
    private String lifecycleState;

    // ── Persona fields ──
    private String personaName;
    private String personaColor;
    private String personaInitials;
}
