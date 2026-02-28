package com.QuantPlatformApplication.QuantPlatformApplication.config;

import com.QuantPlatformApplication.QuantPlatformApplication.model.entity.MarketDataEntity;
import com.QuantPlatformApplication.QuantPlatformApplication.repository.MarketDataRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Seeds the market_data table with synthetic SPY OHLCV data on startup.

 * How it works:
 * 1. Checks if SPY data already exists → skips if so (idempotent).
 * 2. Generates 252 trading days (1 year) of realistic price data.
 * 3. Uses a random walk model starting from ~$450 (approximate SPY price
 * range).
 * 4. Batch-inserts all rows using saveAll() for performance.

 * The generated data mimics real market behavior:
 * - Daily returns follow a normal distribution (~0.04% mean, ~1.2% std dev)
 * - Volume varies between 50M–100M shares
 * - Intraday OHLC relationships are realistic (High ≥ Open, Close, Low)
 * - Weekends are skipped (only business days)

 * In Part 6 of the learning plan, this will be replaced with real Yahoo Finance
 * data.
 */

@Component
public class MarketDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(MarketDataSeeder.class);

    private static final String SYMBOL = "SPY";
    private static final int TRADING_DAYS = 252; // 1 year of trading days
    private static final double STARTING_PRICE = 450.00; // Approximate SPY starting price
    private static final double DAILY_MEAN_RETURN = 0.0004; // ~10% annual return
    private static final double DAILY_STD_DEV = 0.012; // ~19% annual volatility

    private final MarketDataRepository marketDataRepository;

    public MarketDataSeeder(MarketDataRepository marketDataRepository) {
        this.marketDataRepository = marketDataRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        // Skip if data already exists (makes this idempotent)
        if (marketDataRepository.existsBySymbol(SYMBOL)) {
            long count = marketDataRepository.countBySymbol(SYMBOL);
            log.info("✅ SPY data already exists ({} records) — skipping seed.", count);
            return;
        }

        log.info("🌱 Seeding {} trading days of {} data...", TRADING_DAYS, SYMBOL);

        List<MarketDataEntity> bars = generateSyntheticData();
        marketDataRepository.saveAll(bars);

        log.info("✅ Seeded {} records for {}. Price range: ${} → ${}",
                bars.size(), SYMBOL,
                bars.getFirst().getOpen(),
                bars.getLast().getClose());
    }

    /**
     * Generate realistic synthetic OHLCV data using a geometric random walk.
     * 
     * The model:
     * close_today = close_yesterday * exp(mean + std * Z)
     * where Z ~ N(0,1)
     * 
     * This is a simplified version of geometric Brownian motion — the same model
     * used in the Black-Scholes option pricing formula!
     */
    private List<MarketDataEntity> generateSyntheticData() {
        Random random = new Random(42); // Fixed seed for reproducibility
        List<MarketDataEntity> bars = new ArrayList<>();

        double price = STARTING_PRICE;
        // Start from 252 business days ago
        LocalDate date = LocalDate.now().minusDays(365);

        int daysGenerated = 0;
        while (daysGenerated < TRADING_DAYS) {
            // Skip weekends
            if (date.getDayOfWeek() == DayOfWeek.SATURDAY ||
                    date.getDayOfWeek() == DayOfWeek.SUNDAY) {
                date = date.plusDays(1);
                continue;
            }

            // Generate daily return using log-normal model
            double dailyReturn = DAILY_MEAN_RETURN + DAILY_STD_DEV * random.nextGaussian();
            double newPrice = price * Math.exp(dailyReturn);

            // Generate realistic OHLC from the close-to-close move
            double open = price * (1 + (random.nextGaussian() * 0.003)); // Small gap from prev close
            double intradayVol = Math.abs(random.nextGaussian() * 0.008); // Intraday range
            double high = Math.max(open, newPrice) * (1 + intradayVol);
            double low = Math.min(open, newPrice) * (1 - intradayVol);
            double close = newPrice;

            // Volume: 50M–100M + some noise
            long volume = 50_000_000L + (long) (random.nextDouble() * 50_000_000L);

            Instant timestamp = date.atTime(16, 0) // Market close at 4 PM
                    .toInstant(ZoneOffset.UTC);

            MarketDataEntity bar = MarketDataEntity.builder()
                    .time(timestamp)
                    .symbol(SYMBOL)
                    .open(toBigDecimal(open))
                    .high(toBigDecimal(high))
                    .low(toBigDecimal(low))
                    .close(toBigDecimal(close))
                    .volume(volume)
                    .build();

            bars.add(bar);

            price = close;
            date = date.plusDays(1);
            daysGenerated++;
        }

        return bars;
    }

    private BigDecimal toBigDecimal(double value) {
        return BigDecimal.valueOf(value).setScale(4, RoundingMode.HALF_UP);
    }
}
