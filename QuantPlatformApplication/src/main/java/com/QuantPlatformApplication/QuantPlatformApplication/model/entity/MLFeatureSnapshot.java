package com.QuantPlatformApplication.QuantPlatformApplication.model.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "ml_feature_snapshots")
public class MLFeatureSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String symbol;

    @Column(nullable = false)
    private Instant timestamp;

    // Funding rate
    @Column(name = "funding_rate", precision = 12, scale = 8)
    private BigDecimal fundingRate;

    @Column(name = "predicted_funding_rate", precision = 12, scale = 8)
    private BigDecimal predictedFundingRate;

    // Open interest
    @Column(name = "open_interest", precision = 19, scale = 4)
    private BigDecimal openInterest;

    @Column(name = "oi_change_pct", precision = 8, scale = 4)
    private BigDecimal oiChangePct;

    // Basis spread (futures - spot)
    @Column(name = "futures_price", precision = 19, scale = 4)
    private BigDecimal futuresPrice;

    @Column(name = "spot_price", precision = 19, scale = 4)
    private BigDecimal spotPrice;

    @Column(name = "basis_spread", precision = 12, scale = 6)
    private BigDecimal basisSpread;

    @Column(name = "basis_pct", precision = 8, scale = 6)
    private BigDecimal basisPct;

    // Order book imbalance
    @Column(name = "bid_volume_top10", precision = 19, scale = 4)
    private BigDecimal bidVolumeTop10;

    @Column(name = "ask_volume_top10", precision = 19, scale = 4)
    private BigDecimal askVolumeTop10;

    @Column(name = "book_imbalance", precision = 8, scale = 6)
    private BigDecimal bookImbalance;

    // Volume metrics
    @Column(name = "taker_buy_volume", precision = 19, scale = 4)
    private BigDecimal takerBuyVolume;

    @Column(name = "taker_sell_volume", precision = 19, scale = 4)
    private BigDecimal takerSellVolume;

    @Column(name = "volume_imbalance", precision = 8, scale = 6)
    private BigDecimal volumeImbalance;

    // Long/short ratio
    @Column(name = "long_short_ratio", precision = 8, scale = 4)
    private BigDecimal longShortRatio;

    // Metadata
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        createdAt = Instant.now();
    }
}
