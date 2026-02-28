package com.QuantPlatformApplication.QuantPlatformApplication.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

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
}
