package com.QuantPlatformApplication.QuantPlatformApplication.model.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "delta_credentials")
public class DeltaCredential {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "api_key_encrypted", nullable = false, length = 512)
    private String apiKeyEncrypted;

    @Column(name = "api_secret_encrypted", nullable = false, length = 512)
    private String apiSecretEncrypted;

    @Builder.Default
    @Column(name = "is_testnet", nullable = false)
    private Boolean isTestnet = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() { createdAt = Instant.now(); updatedAt = Instant.now(); }

    @PreUpdate
    void preUpdate() { updatedAt = Instant.now(); }
}
