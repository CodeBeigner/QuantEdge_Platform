package com.QuantPlatformApplication.QuantPlatformApplication.repository;

import com.QuantPlatformApplication.QuantPlatformApplication.model.entity.RiskConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface RiskConfigRepository extends JpaRepository<RiskConfig, Long> {
    Optional<RiskConfig> findByUserId(Long userId);
}
