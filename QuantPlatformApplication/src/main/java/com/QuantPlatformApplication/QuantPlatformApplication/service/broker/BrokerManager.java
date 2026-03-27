package com.QuantPlatformApplication.QuantPlatformApplication.service.broker;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Manages multiple broker adapters and routes orders to the active broker.
 */
@Slf4j
@Service
public class BrokerManager {

    private final List<BrokerAdapter> adapters;
    private BrokerAdapter activeBroker;

    @Value("${broker.active:PAPER}")
    private String activeBrokerName;

    public BrokerManager(List<BrokerAdapter> adapters) {
        this.adapters = adapters;
        this.activeBroker = adapters.stream()
                .filter(a -> "PAPER".equals(a.getName()))
                .findFirst()
                .orElse(adapters.get(0));
    }

    @PostConstruct
    void applyConfiguredBroker() {
        if (activeBrokerName == null || activeBrokerName.isBlank()) {
            return;
        }
        try {
            setActiveBroker(activeBrokerName.trim());
        } catch (IllegalArgumentException e) {
            log.warn("broker.active={} not found; keeping default broker {}", activeBrokerName, activeBroker.getName());
        }
    }

    public BrokerAdapter getActiveBroker() {
        return activeBroker;
    }

    public void setActiveBroker(String name) {
        this.activeBroker = adapters.stream()
                .filter(a -> a.getName().equalsIgnoreCase(name))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Broker not found: " + name));
        log.info("Active broker switched to: {}", name);
    }

    public List<Map<String, Object>> listBrokers() {
        return adapters.stream()
                .map(a -> Map.<String, Object>of(
                        "name", a.getName(),
                        "connected", a.isConnected(),
                        "active", a == activeBroker
                ))
                .collect(Collectors.toList());
    }
}
