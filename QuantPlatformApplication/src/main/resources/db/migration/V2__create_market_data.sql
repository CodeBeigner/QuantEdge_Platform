-- V2: Create market_data table for historical OHLCV price data
-- Uses TimescaleDB hypertable for efficient time-series queries

CREATE TABLE IF NOT EXISTS market_data (
    time   TIMESTAMPTZ NOT NULL,
    symbol VARCHAR(20) NOT NULL,
    open   NUMERIC(15,4),
    high   NUMERIC(15,4),
    low    NUMERIC(15,4),
    close  NUMERIC(15,4),
    volume BIGINT,
    PRIMARY KEY (symbol, time)
);

-- Create index for fast lookups by symbol + time (descending for "most recent" queries)
CREATE INDEX IF NOT EXISTS idx_md_symbol_time ON market_data(symbol, time DESC);

-- Convert to TimescaleDB hypertable with 7-day chunks
-- if_not_exists => TRUE prevents errors if already a hypertable
-- DO block provides graceful fallback if TimescaleDB extension is not installed
DO $$
BEGIN
    -- Try to enable TimescaleDB extension
    CREATE EXTENSION IF NOT EXISTS timescaledb CASCADE;

    -- Convert to hypertable
    PERFORM create_hypertable('market_data', 'time',
        chunk_time_interval => INTERVAL '7 days',
        if_not_exists => TRUE,
        migrate_data => TRUE
    );

    RAISE NOTICE 'TimescaleDB hypertable created for market_data';
EXCEPTION
    WHEN OTHERS THEN
        RAISE NOTICE 'TimescaleDB not available — market_data will work as a regular PostgreSQL table. Error: %', SQLERRM;
END $$;
