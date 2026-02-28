package com.QuantPlatformApplication.QuantPlatformApplication.repository;

import com.QuantPlatformApplication.QuantPlatformApplication.model.entity.PortfolioPosition;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface PortfolioPositionRepository extends JpaRepository<PortfolioPosition, Long> {
    Optional<PortfolioPosition> findBySymbol(String symbol);
}
