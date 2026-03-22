package com.QuantPlatformApplication.QuantPlatformApplication.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * JPA entity for trading agents mapped to the {@code trading_agents} table.
 *
 * <p>Stores agent configuration including scheduling, AI role assignment,
 * system prompts, and execution statistics.
 */
@Entity
@Table(name = "trading_agents")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TradingAgent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "strategy_id", nullable = false)
    private Long strategyId;

    @Column(name = "cron_expression", nullable = false, length = 50)
    @Builder.Default
    private String cronExpression = "0 0 9 * * MON-FRI";

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    @Column(name = "last_run_at")
    private Instant lastRunAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();

    // ── AI Agent Layer Fields (V10 migration) ───────────────────────────

    @Enumerated(EnumType.STRING)
    @Column(name = "agent_role", length = 50)
    @Builder.Default
    private AgentRole agentRole = AgentRole.QUANT_RESEARCHER;

    @Column(name = "system_prompt", columnDefinition = "TEXT")
    private String systemPrompt;

    @Column(name = "last_reasoning", columnDefinition = "TEXT")
    private String lastReasoning;

    @Column(name = "last_confidence")
    private Double lastConfidence;

    @Column(name = "total_executions")
    @Builder.Default
    private Integer totalExecutions = 0;

    @Column(name = "successful_executions")
    @Builder.Default
    private Integer successfulExecutions = 0;
}
