package com.QuantPlatformApplication.QuantPlatformApplication.model.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.Instant;

@Data
@Entity
@Table(name = "alerts")
public class Alert {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String alertType; // DRAWDOWN, POSITION_LIMIT, VAR_BREACH, SIGNAL
    private String severity; // INFO, WARNING, CRITICAL
    private String message;
    private String symbol;
    private Boolean acknowledged = false;
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }
}
