package com.QuantPlatformApplication.QuantPlatformApplication.service;

import com.QuantPlatformApplication.QuantPlatformApplication.engine.model.ModelType;
import com.QuantPlatformApplication.QuantPlatformApplication.model.entity.MarketDataEntity;
import com.QuantPlatformApplication.QuantPlatformApplication.model.entity.Strategy;
import java.math.BigDecimal;
import com.QuantPlatformApplication.QuantPlatformApplication.repository.MarketDataRepository;
import com.QuantPlatformApplication.QuantPlatformApplication.repository.StrategyRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Seeds the database with realistic synthetic market data and default
 * strategies
 * on application startup. Only seeds if the database is empty.
 */
@Slf4j
@Component
public class DataSeederService implements CommandLineRunner {

    private final MarketDataRepository marketDataRepo;
    private final StrategyRepository strategyRepo;

    public DataSeederService(MarketDataRepository marketDataRepo,
            StrategyRepository strategyRepo) {
        this.marketDataRepo = marketDataRepo;
        this.strategyRepo = strategyRepo;
    }

    @Override
    public void run(String... args) {
        seedMarketData();
        seedStrategies();
    }

    private void seedMarketData() {
        if (marketDataRepo.findDistinctSymbols().size() > 0) {
            log.info("Market data already exists — skipping seed");
            return;
        }

        log.info("Seeding market data for SPY, AAPL, QQQ...");

        seedSymbol("SPY", 450.0, 0.12, 0.18);
        seedSymbol("AAPL", 175.0, 0.15, 0.25);
        seedSymbol("QQQ", 380.0, 0.14, 0.22);

        log.info("Market data seeded: 3 symbols × 504 days");
    }

    private void seedSymbol(String symbol, double startPrice, double annualReturn, double annualVol) {
        Random rng = new Random(symbol.hashCode());
        double dailyReturn = annualReturn / 252.0;
        double dailyVol = annualVol / Math.sqrt(252.0);

        List<MarketDataEntity> batch = new ArrayList<>();
        double price = startPrice;

        // 2 years of daily data
        LocalDate date = LocalDate.of(2024, 1, 2);
        LocalDate end = LocalDate.of(2025, 12, 31);

        while (!date.isAfter(end)) {
            // Skip weekends
            if (date.getDayOfWeek().getValue() >= 6) {
                date = date.plusDays(1);
                continue;
            }

            double change = dailyReturn + dailyVol * rng.nextGaussian();
            double open = price;
            double close = price * (1 + change);
            double high = Math.max(open, close) * (1 + Math.abs(rng.nextGaussian()) * 0.005);
            double low = Math.min(open, close) * (1 - Math.abs(rng.nextGaussian()) * 0.005);
            long volume = (long) (5_000_000 + rng.nextGaussian() * 2_000_000);
            if (volume < 500_000)
                volume = 500_000;

            Instant time = date.atStartOfDay(ZoneOffset.UTC).toInstant();

            MarketDataEntity entity = new MarketDataEntity();
            entity.setTime(time);
            entity.setSymbol(symbol);
            entity.setOpen(BigDecimal.valueOf(round(open)));
            entity.setHigh(BigDecimal.valueOf(round(high)));
            entity.setLow(BigDecimal.valueOf(round(low)));
            entity.setClose(BigDecimal.valueOf(round(close)));
            entity.setVolume(volume);

            batch.add(entity);
            price = close;
            date = date.plusDays(1);
        }

        marketDataRepo.saveAll(batch);
        log.info("  {} → {} records, final price ${}", symbol, batch.size(), round(price));
    }

    private void seedStrategies() {
        if (strategyRepo.count() > 0) {
            log.info("Strategies already exist — skipping seed");
            return;
        }

        log.info("Seeding default strategies...");

        createStrategy("SPY Momentum Crossover", "SPY", ModelType.MOMENTUM,
                100_000.0, 1.0, 10_000.0);
        createStrategy("AAPL Volatility Targeting", "AAPL", ModelType.VOLATILITY,
                100_000.0, 1.0, 8_000.0);
        createStrategy("Macro Rate Allocation", "SPY", ModelType.MACRO,
                200_000.0, 1.0, 15_000.0);
        createStrategy("SPY-QQQ Correlation", "SPY", ModelType.CORRELATION,
                150_000.0, 1.0, 12_000.0);
        createStrategy("Multi-Signal Regime", "SPY", ModelType.REGIME,
                250_000.0, 1.0, 20_000.0);

        log.info("Seeded 5 default strategies");
    }

    private void createStrategy(String name, String symbol, ModelType type,
            double cash, double multiplier, double risk) {
        Strategy s = new Strategy();
        s.setName(name);
        s.setSymbol(symbol);
        s.setModelType(type);
        s.setCurrentCash(BigDecimal.valueOf(cash));
        s.setPositionMultiplier(BigDecimal.valueOf(multiplier));
        s.setTargetRisk(BigDecimal.valueOf(risk));
        s.setCreatedAt(Instant.now());
        s.setUpdatedAt(Instant.now());
        strategyRepo.save(s);
    }

    private double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
