package com.QuantPlatformApplication.QuantPlatformApplication.repository;

import com.QuantPlatformApplication.QuantPlatformApplication.model.entity.BacktestResult;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface BacktestResultRepository extends JpaRepository<BacktestResult, Long> {
    List<BacktestResult> findByStrategyIdOrderByCreatedAtDesc(Long strategyId);

    /**
     * Find the most recent backtest result for a given strategy.
     *
     * @param strategyId the strategy ID
     * @return the latest BacktestResult, or empty if none exist
     */
    Optional<BacktestResult> findTopByStrategyIdOrderByCreatedAtDesc(Long strategyId);
}
