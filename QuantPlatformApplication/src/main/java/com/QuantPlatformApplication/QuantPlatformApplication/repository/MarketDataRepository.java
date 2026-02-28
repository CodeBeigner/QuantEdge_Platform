package com.QuantPlatformApplication.QuantPlatformApplication.repository;

import com.QuantPlatformApplication.QuantPlatformApplication.model.entity.MarketDataEntity;
import com.QuantPlatformApplication.QuantPlatformApplication.model.entity.MarketDataId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

/**
 * Repository for querying the market_data hypertable.

 * Provides three ways to fetch price data:
 * 1. By symbol + date range (derived query)
 * 2. By symbol + last N days (native SQL — most useful for API)
 * 3. List distinct symbols (native SQL)
 */
public interface MarketDataRepository extends JpaRepository<MarketDataEntity, MarketDataId> {

    /**
     * Fetch all OHLCV data for a symbol within a date range, ordered
     * chronologically.
     * Spring Data JPA generates the query automatically from the method name.
     */
    List<MarketDataEntity> findBySymbolAndTimeBetweenOrderByTimeAsc(String symbol, Instant start, Instant end);

    /**
     * Fetch the most recent N rows for a given symbol.
     * Uses native SQL with ORDER BY + LIMIT for efficient pagination on
     * TimescaleDB.

     * Why native query? Spring Data's derived queries don't support LIMIT natively,
     * and we want to leverage TimescaleDB's chunk-aware index scans.
     */
    @Query(value = """
            SELECT * FROM market_data
            WHERE symbol = :symbol
            ORDER BY time DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<MarketDataEntity> findRecentBySymbol(
            @Param("symbol") String symbol,
            @Param("limit") int limit);

    /**
     * List all distinct symbols in the market_data table.
     * Useful for the /symbols endpoint and data discovery.
     */
    @Query(value = "SELECT DISTINCT symbol FROM market_data ORDER BY symbol", nativeQuery = true)
    List<String> findDistinctSymbols();

    /**
     * Check if any data exists for a given symbol.
     * Used by the seeder to avoid duplicate inserts.
     */
    boolean existsBySymbol(String symbol);

    /**
     * Count rows for a given symbol (useful for diagnostics).
     */
    long countBySymbol(String symbol);
}
