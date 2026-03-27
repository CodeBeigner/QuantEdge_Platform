package com.QuantPlatformApplication.QuantPlatformApplication.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * JPA entity representing a trading firm profile.
 *
 * <p>Each user owns one firm. The firm type determines which agents are
 * spawned, what their mandates are, and how risk limits are applied.
 */
@Entity
@Table(name = "firm_profiles")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FirmProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "firm_name", nullable = false, length = 100)
    private String firmName;

    @Enumerated(EnumType.STRING)
    @Column(name = "firm_type", nullable = false, length = 50)
    private FirmType firmType;

    @Column(name = "initial_capital", precision = 20, scale = 2)
    private BigDecimal initialCapital;

    @Column(name = "risk_appetite", length = 20)
    @Builder.Default
    private String riskAppetite = "MODERATE";

    @Column(name = "setup_complete")
    @Builder.Default
    private Boolean setupComplete = false;

    @Column(name = "owner_user_id")
    private Long ownerUserId;

    @Column(name = "created_at", updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at")
    @Builder.Default
    private Instant updatedAt = Instant.now();
}
