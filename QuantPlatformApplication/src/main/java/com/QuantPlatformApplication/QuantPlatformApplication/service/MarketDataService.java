package com.QuantPlatformApplication.QuantPlatformApplication.service;

import com.QuantPlatformApplication.QuantPlatformApplication.model.entity.MarketDataEntity;
import com.QuantPlatformApplication.QuantPlatformApplication.repository.MarketDataRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

/**
 * Service layer for market data operations.

 * Handles all business logic around price data:
 * - Fetching by date range
 * - Fetching the most recent N days
 * - Listing available symbols

 * This layer sits between the controller (REST) and repository (DB).
 * In Part 6, this will also handle cache-aside logic (check DB → call Yahoo
 * Finance → persist).
 */
@Service
public class MarketDataService {

    private final MarketDataRepository marketDataRepository;

    public MarketDataService(MarketDataRepository marketDataRepository) {
        this.marketDataRepository = marketDataRepository;
    }

    /**
     * Fetch OHLCV data for a symbol within a specific date range.
     *
     * @param symbol Stock ticker (e.g., "SPY")
     * @param start  Start of date range (inclusive)
     * @param end    End of date range (inclusive)
     * @return List of price bars ordered chronologically
     */
    public List<MarketDataEntity> fetchDailyData(String symbol, Instant start, Instant end) {
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("Symbol must not be blank");
        }
        if (start.isAfter(end)) {
            throw new IllegalArgumentException("Start date must be before end date");
        }

        return marketDataRepository.findBySymbolAndTimeBetweenOrderByTimeAsc(
                symbol.toUpperCase(), start, end);
    }

    /**
     * Fetch the most recent N trading days of data for a symbol.
     * The results are returned in ascending chronological order (oldest first).
     *
     * @param symbol Stock ticker (e.g., "SPY")
     * @param days   Number of recent trading days to fetch
     * @return List of price bars ordered chronologically (oldest → newest)
     */
    public List<MarketDataEntity> fetchRecentData(String symbol, int days) {
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("Symbol must not be blank");
        }
        if (days <= 0 || days > 5000) {
            throw new IllegalArgumentException("Days must be between 1 and 5000");
        }

        // The native query returns data in DESC order (most recent first)
        // We reverse it to provide chronological order (oldest first) — more natural
        // for charts
        List<MarketDataEntity> results = marketDataRepository.findRecentBySymbol(
                symbol.toUpperCase(), days);

        // Reverse to chronological order
        List<MarketDataEntity> chronological = new java.util.ArrayList<>(results);
        Collections.reverse(chronological);
        return chronological;
    }

    /**
     * Get all symbols that have data in the database.
     *
     * @return List of distinct ticker symbols (e.g., ["SPY", "AAPL"])
     */
    public List<String> getAvailableSymbols() {
        return marketDataRepository.findDistinctSymbols();
    }

    /**
     * Get the total count of records for a given symbol.
     * Useful for diagnostics and the frontend summary.
     */
    public long getRecordCount(String symbol) {
        return marketDataRepository.countBySymbol(symbol.toUpperCase());
    }
}
