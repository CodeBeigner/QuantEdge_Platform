package com.QuantPlatformApplication.QuantPlatformApplication.repository;

import com.QuantPlatformApplication.QuantPlatformApplication.model.entity.AgentConversation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * JPA repository for {@link AgentConversation} entities.
 */
public interface AgentConversationRepository extends JpaRepository<AgentConversation, Long> {

    /**
     * Get the most recent 20 messages for an agent, newest first.
     *
     * @param agentId the agent ID
     * @return list of recent conversations, newest first
     */
    List<AgentConversation> findTop20ByAgentIdOrderByCreatedAtDesc(Long agentId);

    /**
     * Get all messages for an agent in chronological order.
     *
     * @param agentId the agent ID
     * @return list of conversations, oldest first
     */
    List<AgentConversation> findByAgentIdOrderByCreatedAtAsc(Long agentId);

    /**
     * Delete all conversation messages for an agent.
     *
     * @param agentId the agent ID
     */
    void deleteByAgentId(Long agentId);
}
