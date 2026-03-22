package com.QuantPlatformApplication.QuantPlatformApplication.repository;

import com.QuantPlatformApplication.QuantPlatformApplication.model.entity.SignalPrediction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;

/**
 * Repository for the signal_predictions table.
 */
public interface SignalPredictionRepository extends JpaRepository<SignalPrediction, Long> {

    /**
     * Find recent predictions for a strategy, ordered newest first.
     */
    List<SignalPrediction> findByStrategyIdAndCreatedAtAfterOrderByCreatedAtDesc(
            Long strategyId, Instant after);

    /**
     * Find unresolved predictions (where actual_return is null).
     */
    List<SignalPrediction> findByActualReturnIsNull();

    /**
     * Find resolved predictions for IC computation over a window.
     */
    @Query("SELECT sp FROM SignalPrediction sp WHERE sp.strategyId = :strategyId " +
           "AND sp.actualReturn IS NOT NULL AND sp.createdAt > :since " +
           "ORDER BY sp.createdAt DESC")
    List<SignalPrediction> findResolvedByStrategy(Long strategyId, Instant since);
}
