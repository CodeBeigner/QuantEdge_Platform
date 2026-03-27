package com.QuantPlatformApplication.QuantPlatformApplication.service;

import com.QuantPlatformApplication.QuantPlatformApplication.model.entity.AgentConversation;
import com.QuantPlatformApplication.QuantPlatformApplication.model.entity.TradingAgent;
import com.QuantPlatformApplication.QuantPlatformApplication.repository.AgentConversationRepository;
import com.QuantPlatformApplication.QuantPlatformApplication.repository.TradingAgentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Service orchestrating conversations between the CEO and AI agents.
 * Persists conversation history and integrates with ClaudeAgentService for replies.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentConversationService {

    private final AgentConversationRepository conversationRepo;
    private final TradingAgentRepository agentRepo;
    private final ClaudeAgentService claudeService;

    /**
     * Send a message to a specific agent and get a conversational reply.
     * Persists both the user message and assistant reply.
     *
     * @param agentId     the agent's database ID
     * @param userMessage the CEO's message
     * @return map with agentId, agentName, agentRole, reply, timestamp
     */
    @Transactional
    public Map<String, Object> chat(Long agentId, String userMessage) {
        TradingAgent agent = agentRepo.findById(agentId)
                .orElseThrow(() -> new IllegalArgumentException("Agent not found: " + agentId));

        // Load recent conversation history (last 20 messages, reversed to chronological)
        List<AgentConversation> history = conversationRepo
                .findTop20ByAgentIdOrderByCreatedAtDesc(agentId);
        Collections.reverse(history);

        List<Map<String, String>> historyMaps = history.stream()
                .map(c -> Map.of("role", c.getRole(), "content", c.getContent()))
                .toList();

        String personaName = agent.getPersonaName() != null
                ? agent.getPersonaName() : agent.getName();
        String systemPrompt = agent.getSystemPrompt() != null
                ? agent.getSystemPrompt() : "";

        // Persist user message
        AgentConversation userMsg = AgentConversation.builder()
                .agentId(agentId)
                .role("user")
                .content(userMessage)
                .build();
        conversationRepo.save(userMsg);

        // Get agent reply via Claude
        String reply = claudeService.chatWithAgent(
                agentId, userMessage, historyMaps, systemPrompt, personaName);

        // Persist agent reply
        AgentConversation assistantMsg = AgentConversation.builder()
                .agentId(agentId)
                .role("assistant")
                .content(reply)
                .build();
        conversationRepo.save(assistantMsg);

        // Update agent's last reasoning with truncated reply
        agent.setLastReasoning(reply.length() > 500 ? reply.substring(0, 500) : reply);
        agent.setUpdatedAt(Instant.now());
        agentRepo.save(agent);

        log.info("Chat completed: agentId={}, agentName={}", agentId, personaName);

        return Map.of(
                "agentId", agentId,
                "agentName", personaName,
                "agentRole", agent.getAgentRole().name(),
                "reply", reply,
                "timestamp", Instant.now().toString()
        );
    }

    /**
     * Get full conversation history for an agent in chronological order.
     *
     * @param agentId the agent ID
     * @return list of conversation messages
     */
    @Transactional(readOnly = true)
    public List<AgentConversation> getHistory(Long agentId) {
        return conversationRepo.findByAgentIdOrderByCreatedAtAsc(agentId);
    }

    /**
     * Clear all conversation history for an agent.
     *
     * @param agentId the agent ID
     */
    @Transactional
    public void clearHistory(Long agentId) {
        conversationRepo.deleteByAgentId(agentId);
        log.info("Cleared conversation history for agentId={}", agentId);
    }
}
