package com.QuantPlatformApplication.QuantPlatformApplication.repository;

import com.QuantPlatformApplication.QuantPlatformApplication.model.entity.BacktestResult;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface BacktestResultRepository extends JpaRepository<BacktestResult, Long> {
    List<BacktestResult> findByStrategyIdOrderByCreatedAtDesc(Long strategyId);
}
