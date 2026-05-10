-- V22: Multi-timeframe market_data + derivatives history
-- Rationale: existing PK (symbol, time) cannot hold 15m and 1h rows for the
-- same symbol. Adds timeframe column and creates funding_rate_history and
-- open_interest_history hypertables for historical backtesting.

-- 1) Extend market_data with timeframe
ALTER TABLE market_data ADD COLUMN IF NOT EXISTS timeframe VARCHAR(8) NOT NULL DEFAULT '15m';

-- 2) Drop old PK, add new composite PK
ALTER TABLE market_data DROP CONSTRAINT IF EXISTS market_data_pkey;
ALTER TABLE market_data ADD CONSTRAINT market_data_pkey PRIMARY KEY (symbol, timeframe, time);

-- 3) Index optimized for range scans per symbol+timeframe
CREATE INDEX IF NOT EXISTS idx_market_data_symbol_tf_time
    ON market_data (symbol, timeframe, time DESC);

-- 4) Funding rate history (Binance publishes every 8 hours)
CREATE TABLE IF NOT EXISTS funding_rate_history (
    symbol       VARCHAR(32)     NOT NULL,
    time         TIMESTAMPTZ     NOT NULL,
    funding_rate NUMERIC(20, 10) NOT NULL,
    mark_price   NUMERIC(20, 8),
    PRIMARY KEY (symbol, time)
);

SELECT create_hypertable(
    'funding_rate_history',
    'time',
    chunk_time_interval => INTERVAL '30 days',
    if_not_exists => TRUE
);

-- 5) Open interest history (supports multiple periods per symbol)
CREATE TABLE IF NOT EXISTS open_interest_history (
    symbol        VARCHAR(32)     NOT NULL,
    time          TIMESTAMPTZ     NOT NULL,
    period        VARCHAR(8)      NOT NULL,
    open_interest NUMERIC(24, 8)  NOT NULL,
    PRIMARY KEY (symbol, period, time)
);

SELECT create_hypertable(
    'open_interest_history',
    'time',
    chunk_time_interval => INTERVAL '30 days',
    if_not_exists => TRUE
);
