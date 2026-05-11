-- V23: Allow fractional volume on market_data
-- Binance crypto volume is fractional (e.g., 3617.988 BTC). The original V2
-- schema used BIGINT, which rejects decimals. NUMERIC(20,8) matches the
-- precision used for funding/OI tables added in V22.

ALTER TABLE market_data
    ALTER COLUMN volume TYPE NUMERIC(20, 8) USING volume::NUMERIC(20, 8);
