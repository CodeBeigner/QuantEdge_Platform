CREATE TABLE ml_feature_snapshots (
    id              BIGSERIAL PRIMARY KEY,
    symbol          VARCHAR(20) NOT NULL,
    timestamp       TIMESTAMPTZ NOT NULL,
    -- Funding rate
    funding_rate    NUMERIC(12,8),
    predicted_funding_rate NUMERIC(12,8),
    -- Open interest
    open_interest   NUMERIC(19,4),
    oi_change_pct   NUMERIC(8,4),
    -- Basis spread (futures - spot)
    futures_price   NUMERIC(19,4),
    spot_price      NUMERIC(19,4),
    basis_spread    NUMERIC(12,6),
    basis_pct       NUMERIC(8,6),
    -- Order book imbalance
    bid_volume_top10 NUMERIC(19,4),
    ask_volume_top10 NUMERIC(19,4),
    book_imbalance  NUMERIC(8,6),
    -- Volume metrics
    taker_buy_volume NUMERIC(19,4),
    taker_sell_volume NUMERIC(19,4),
    volume_imbalance NUMERIC(8,6),
    -- Long/short ratio
    long_short_ratio NUMERIC(8,4),
    -- Metadata
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_ml_features_symbol_ts ON ml_feature_snapshots(symbol, timestamp);
CREATE INDEX idx_ml_features_ts ON ml_feature_snapshots(timestamp);
