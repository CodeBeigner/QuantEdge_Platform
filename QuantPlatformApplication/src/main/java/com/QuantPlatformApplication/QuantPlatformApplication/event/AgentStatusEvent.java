package com.QuantPlatformApplication.QuantPlatformApplication.event;

import java.time.Instant;

/**
 * Event record broadcast via WebSocket to update agent status in real time.
 * Published to /topic/agents for the trading floor UI.
 *
 * @param agentId      the agent's database ID
 * @param agentName    the agent's persona name
 * @param agentRole    the agent's role (e.g. QUANT_RESEARCHER)
 * @param status       current status: IDLE, RESEARCHING, ANALYSING, TRADING, ALERT, CHATTING
 * @param activityText human-readable description of current activity
 * @param confidence   current confidence level (0.0–1.0)
 * @param timestamp    when this status was emitted
 */
public record AgentStatusEvent(
    Long agentId,
    String agentName,
    String agentRole,
    String status,
    String activityText,
    Double confidence,
    Instant timestamp
) {}
