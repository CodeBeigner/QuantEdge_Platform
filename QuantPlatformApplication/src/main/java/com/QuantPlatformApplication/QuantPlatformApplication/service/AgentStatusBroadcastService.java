package com.QuantPlatformApplication.QuantPlatformApplication.service;

import com.QuantPlatformApplication.QuantPlatformApplication.event.AgentStatusEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Broadcasts agent status updates to all connected WebSocket clients.
 * Publishes to /topic/agents so the trading floor can display real-time activity.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentStatusBroadcastService {

    private final SimpMessagingTemplate messagingTemplate;

    /** WebSocket topic for agent status updates */
    private static final String AGENTS_TOPIC = "/topic/agents";

    /**
     * Broadcast an agent's current status to all connected clients.
     *
     * @param agentId      the agent's database ID
     * @param agentName    the agent's persona name
     * @param role         the agent's role string
     * @param status       current status (IDLE, RESEARCHING, ANALYSING, TRADING, ALERT, CHATTING)
     * @param activityText human-readable activity description
     * @param confidence   confidence level (0.0–1.0, nullable)
     */
    public void broadcastAgentStatus(Long agentId, String agentName,
            String role, String status, String activityText, Double confidence) {
        AgentStatusEvent event = new AgentStatusEvent(
                agentId, agentName, role, status, activityText,
                confidence, Instant.now());
        messagingTemplate.convertAndSend(AGENTS_TOPIC, event);
        log.debug("Broadcast agent status: agentId={}, status={}, activity={}",
                agentId, status, activityText);
    }
}
