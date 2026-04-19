package com.QuantPlatformApplication.QuantPlatformApplication.service;

import com.QuantPlatformApplication.QuantPlatformApplication.client.BinanceMarketDataClient;
import com.QuantPlatformApplication.QuantPlatformApplication.model.entity.MLFeatureSnapshot;
import com.QuantPlatformApplication.QuantPlatformApplication.repository.MLFeatureSnapshotRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Collects alternative ML features from Binance — the data that gives real
 * predictive edge beyond commodity TA indicators (RSI, MACD, etc.).
 *
 * Features collected:
 *   - Funding rate & predicted funding rate
 *   - Open interest & OI change percentage
 *   - Basis spread (futures price - spot price) & basis percentage
 *   - Order book imbalance (bid vs ask volume, top 10 levels)
 *   - Taker buy/sell volume imbalance
 *   - Global long/short account ratio
 *
 * Runs on a 5-minute schedule (disabled by default via ml.feature-collection.enabled).
 */
@Slf4j
@Service
public class MLFeatureCollector {

    private static final MathContext MC = new MathContext(12, RoundingMode.HALF_UP);

    private final BinanceMarketDataClient marketDataClient;
    private final MLFeatureSnapshotRepository repository;

    @Value("${ml.feature-collection.enabled:false}")
    private boolean enabled;

    public MLFeatureCollector(BinanceMarketDataClient marketDataClient,
                              MLFeatureSnapshotRepository repository) {
        this.marketDataClient = marketDataClient;
        this.repository = repository;
    }

    /**
     * Scheduled collection every 5 minutes. Only runs when enabled via config.
     */
    @Scheduled(fixedRate = 300_000)
    public void collectScheduled() {
        if (!enabled) return;
        for (String symbol : List.of("BTCUSDT", "ETHUSDT")) {
            try {
                collectAndStore(symbol);
            } catch (Exception e) {
                log.error("Scheduled collection failed for {}: {}", symbol, e.getMessage(), e);
            }
        }
    }

    /**
     * Collect all ML features for a symbol and persist to the database.
     * Can be called on-demand via the REST API.
     */
    public MLFeatureSnapshot collectAndStore(String symbol) {
        log.info("Collecting ML features for {}", symbol);
        Instant now = Instant.now();

        MLFeatureSnapshot.MLFeatureSnapshotBuilder builder = MLFeatureSnapshot.builder()
                .symbol(symbol)
                .timestamp(now);

        // 1. Funding rate
        collectFundingRate(symbol, builder);

        // 2. Open interest
        collectOpenInterest(symbol, builder);

        // 3. Basis spread (futures vs spot)
        collectBasisSpread(symbol, builder);

        // 4. Order book imbalance
        collectOrderBookImbalance(symbol, builder);

        // 5. Taker buy/sell volume
        collectTakerVolume(symbol, builder);

        // 6. Long/short ratio
        collectLongShortRatio(symbol, builder);

        MLFeatureSnapshot snapshot = builder.build();
        MLFeatureSnapshot saved = repository.save(snapshot);
        log.info("Saved ML feature snapshot id={} for {} at {}", saved.getId(), symbol, now);
        return saved;
    }

    // ---- Private collectors (each handles its own errors) ----

    private void collectFundingRate(String symbol, MLFeatureSnapshot.MLFeatureSnapshotBuilder builder) {
        try {
            Map<String, Object> data = marketDataClient.getFundingRate(symbol);
            if (!data.isEmpty()) {
                String lastFunding = safeString(data, "lastFundingRate");
                if (lastFunding != null) {
                    builder.fundingRate(new BigDecimal(lastFunding));
                }
                // markPrice serves as the futures price indicator
                String markPrice = safeString(data, "markPrice");
                if (markPrice != null) {
                    builder.futuresPrice(new BigDecimal(markPrice));
                }
            }
        } catch (Exception e) {
            log.warn("Error collecting funding rate for {}: {}", symbol, e.getMessage());
        }
    }

    private void collectOpenInterest(String symbol, MLFeatureSnapshot.MLFeatureSnapshotBuilder builder) {
        try {
            Map<String, Object> data = marketDataClient.getOpenInterest(symbol);
            if (!data.isEmpty()) {
                String oi = safeString(data, "openInterest");
                if (oi != null) {
                    BigDecimal currentOI = new BigDecimal(oi);
                    builder.openInterest(currentOI);

                    // Compute OI change % from last snapshot
                    computeOiChangePct(symbol, currentOI, builder);
                }
            }
        } catch (Exception e) {
            log.warn("Error collecting open interest for {}: {}", symbol, e.getMessage());
        }
    }

    private void computeOiChangePct(String symbol, BigDecimal currentOI,
                                     MLFeatureSnapshot.MLFeatureSnapshotBuilder builder) {
        try {
            List<MLFeatureSnapshot> recent = repository.findTop100BySymbolOrderByTimestampDesc(symbol);
            if (!recent.isEmpty() && recent.get(0).getOpenInterest() != null) {
                BigDecimal prevOI = recent.get(0).getOpenInterest();
                if (prevOI.compareTo(BigDecimal.ZERO) != 0) {
                    BigDecimal changePct = currentOI.subtract(prevOI)
                            .divide(prevOI, 4, RoundingMode.HALF_UP)
                            .multiply(new BigDecimal("100"));
                    builder.oiChangePct(changePct);
                }
            }
        } catch (Exception e) {
            log.warn("Error computing OI change for {}: {}", symbol, e.getMessage());
        }
    }

    private void collectBasisSpread(String symbol, MLFeatureSnapshot.MLFeatureSnapshotBuilder builder) {
        try {
            double spotPriceVal = marketDataClient.getSpotPrice(symbol);
            if (spotPriceVal > 0) {
                BigDecimal spot = BigDecimal.valueOf(spotPriceVal);
                builder.spotPrice(spot);

                // Futures price may already be set from funding rate (markPrice)
                // If not, we use the premium index mark price. Either way, compute basis
                // after building — we do it inline here if we have both values
                Map<String, Object> fundingData = marketDataClient.getFundingRate(symbol);
                String markPriceStr = safeString(fundingData, "markPrice");
                if (markPriceStr != null) {
                    BigDecimal futures = new BigDecimal(markPriceStr);
                    builder.futuresPrice(futures);
                    BigDecimal basisSpread = futures.subtract(spot);
                    builder.basisSpread(basisSpread);
                    if (spot.compareTo(BigDecimal.ZERO) != 0) {
                        BigDecimal basisPct = basisSpread.divide(spot, 6, RoundingMode.HALF_UP);
                        builder.basisPct(basisPct);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Error collecting basis spread for {}: {}", symbol, e.getMessage());
        }
    }

    private void collectOrderBookImbalance(String symbol, MLFeatureSnapshot.MLFeatureSnapshotBuilder builder) {
        try {
            Map<String, Object> data = marketDataClient.getOrderBookDepth(symbol, 10);
            if (!data.isEmpty()) {
                BigDecimal bidVolume = sumOrderBookVolume(data, "bids");
                BigDecimal askVolume = sumOrderBookVolume(data, "asks");

                builder.bidVolumeTop10(bidVolume);
                builder.askVolumeTop10(askVolume);

                BigDecimal total = bidVolume.add(askVolume);
                if (total.compareTo(BigDecimal.ZERO) != 0) {
                    // Imbalance: (bid - ask) / (bid + ask) => range [-1, 1]
                    BigDecimal imbalance = bidVolume.subtract(askVolume)
                            .divide(total, 6, RoundingMode.HALF_UP);
                    builder.bookImbalance(imbalance);
                }
            }
        } catch (Exception e) {
            log.warn("Error collecting order book for {}: {}", symbol, e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private BigDecimal sumOrderBookVolume(Map<String, Object> data, String side) {
        BigDecimal total = BigDecimal.ZERO;
        Object levels = data.get(side);
        if (levels instanceof List<?> list) {
            for (Object level : list) {
                if (level instanceof List<?> pair && pair.size() >= 2) {
                    total = total.add(new BigDecimal(pair.get(1).toString()));
                }
            }
        }
        return total;
    }

    private void collectTakerVolume(String symbol, MLFeatureSnapshot.MLFeatureSnapshotBuilder builder) {
        try {
            Map<String, Object> data = marketDataClient.getTakerBuySellVolume(symbol);
            if (!data.isEmpty()) {
                String buyVol = safeString(data, "buyVol");
                String sellVol = safeString(data, "sellVol");
                if (buyVol != null && sellVol != null) {
                    BigDecimal buy = new BigDecimal(buyVol);
                    BigDecimal sell = new BigDecimal(sellVol);
                    builder.takerBuyVolume(buy);
                    builder.takerSellVolume(sell);

                    BigDecimal total = buy.add(sell);
                    if (total.compareTo(BigDecimal.ZERO) != 0) {
                        BigDecimal imbalance = buy.subtract(sell)
                                .divide(total, 6, RoundingMode.HALF_UP);
                        builder.volumeImbalance(imbalance);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Error collecting taker volume for {}: {}", symbol, e.getMessage());
        }
    }

    private void collectLongShortRatio(String symbol, MLFeatureSnapshot.MLFeatureSnapshotBuilder builder) {
        try {
            Map<String, Object> data = marketDataClient.getLongShortRatio(symbol);
            if (!data.isEmpty()) {
                String ratio = safeString(data, "longShortRatio");
                if (ratio != null) {
                    builder.longShortRatio(new BigDecimal(ratio));
                }
            }
        } catch (Exception e) {
            log.warn("Error collecting long/short ratio for {}: {}", symbol, e.getMessage());
        }
    }

    // ---- Utility ----

    private String safeString(Map<String, Object> map, String key) {
        Object val = map.get(key);
        return val != null ? val.toString() : null;
    }
}
