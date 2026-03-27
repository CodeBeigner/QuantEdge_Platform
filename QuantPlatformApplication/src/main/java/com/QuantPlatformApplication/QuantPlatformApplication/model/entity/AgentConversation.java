package com.QuantPlatformApplication.QuantPlatformApplication.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * JPA entity for storing conversation messages between the CEO and AI agents.
 * Each record is a single message in a conversation thread tied to one agent.
 */
@Entity
@Table(name = "agent_conversations")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentConversation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "agent_id", nullable = false)
    private Long agentId;

    /** Message role: "user" (CEO) or "assistant" (agent). */
    @Column(nullable = false, length = 20)
    private String role;

    /** Message content in plain text. */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "created_at", updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
