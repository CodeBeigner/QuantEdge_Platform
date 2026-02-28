package com.QuantPlatformApplication.QuantPlatformApplication.repository;

import com.QuantPlatformApplication.QuantPlatformApplication.engine.model.ModelType;
import com.QuantPlatformApplication.QuantPlatformApplication.model.entity.Strategy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Repository for the strategies table.
 *
 * Provides standard CRUD plus finder methods for filtering by
 * model type, symbol, and active status.
 */
public interface StrategyRepository extends JpaRepository<Strategy, Long> {

    List<Strategy> findByModelType(ModelType modelType);

    List<Strategy> findBySymbol(String symbol);

    List<Strategy> findByActiveTrue();
}
