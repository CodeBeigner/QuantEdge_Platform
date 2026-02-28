package com.QuantPlatformApplication.QuantPlatformApplication.service;

import com.QuantPlatformApplication.QuantPlatformApplication.model.entity.MarketDataEntity;
import com.QuantPlatformApplication.QuantPlatformApplication.repository.MarketDataRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;

/**
 * Simulates live market data ticks and publishes them via WebSocket.
 *
 * Every 3 seconds, generates a small price movement for each tracked symbol
 * and broadcasts to /topic/prices/{symbol}. Clients subscribe to receive
 * real-time price updates.
 *
 * Also records custom Prometheus metrics for monitoring.
 */
@Slf4j
@Service
public class PricePublisherService {

    private final SimpMessagingTemplate messagingTemplate;
    private final MarketDataRepository marketDataRepo;
    private final MeterRegistry meterRegistry;
    private final Timer pricePublishTimer;

    private final Map<String, BigDecimal> lastPrices = new HashMap<>();
    private final Random rng = new Random();

    public PricePublisherService(SimpMessagingTemplate messagingTemplate,
            MarketDataRepository marketDataRepo,
            MeterRegistry meterRegistry) {
        this.messagingTemplate = messagingTemplate;
        this.marketDataRepo = marketDataRepo;
        this.meterRegistry = meterRegistry;
        this.pricePublishTimer = Timer.builder("quantedge.price.publish")
                .description("Time to generate and publish a price tick")
                .register(meterRegistry);

        // Register counters
        meterRegistry.counter("quantedge.ticks.total", "type", "published");
    }

    /**
     * Publish simulated live ticks every 3 seconds.
     * Uses the last known close price and applies a small random walk.
     */
    @Scheduled(fixedRate = 3000)
    public void publishPriceTicks() {
        List<String> symbols = marketDataRepo.findDistinctSymbols();
        if (symbols.isEmpty())
            return;

        pricePublishTimer.record(() -> {
            for (String symbol : symbols) {
                BigDecimal price = getOrInitPrice(symbol);
                BigDecimal tick = simulateTick(price);
                lastPrices.put(symbol, tick);

                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("symbol", symbol);
                payload.put("price", tick);
                payload.put("change", tick.subtract(price).setScale(2, RoundingMode.HALF_UP));
                payload.put("changePercent",
                        tick.subtract(price).divide(price, 6, RoundingMode.HALF_UP)
                                .multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP));
                payload.put("volume", 10000 + rng.nextInt(90000));
                payload.put("timestamp", Instant.now().toString());

                messagingTemplate.convertAndSend("/topic/prices/" + symbol, payload);
                messagingTemplate.convertAndSend("/topic/prices", payload);

                meterRegistry.counter("quantedge.ticks.total", "type", "published").increment();
            }
        });
    }

    private BigDecimal getOrInitPrice(String symbol) {
        if (lastPrices.containsKey(symbol)) {
            return lastPrices.get(symbol);
        }
        // Initialize from the most recent DB record
        List<MarketDataEntity> recent = marketDataRepo.findRecentBySymbol(symbol, 1);
        BigDecimal price = recent.isEmpty()
                ? BigDecimal.valueOf(100.00)
                : recent.get(0).getClose();
        lastPrices.put(symbol, price);
        return price;
    }

    private BigDecimal simulateTick(BigDecimal price) {
        // Random walk: small percentage change (-0.3% to +0.3%)
        double pctChange = (rng.nextGaussian() * 0.003);
        BigDecimal delta = price.multiply(BigDecimal.valueOf(pctChange));
        return price.add(delta).setScale(2, RoundingMode.HALF_UP);
    }
}
