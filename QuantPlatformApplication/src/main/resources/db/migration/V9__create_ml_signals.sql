-- ML signals table
CREATE TABLE IF NOT EXISTS ml_signals (
    id BIGSERIAL PRIMARY KEY,
    symbol VARCHAR(20) NOT NULL,
    signal VARCHAR(10) NOT NULL,         -- BUY, SELL, HOLD
    confidence NUMERIC(10,4),
    model_accuracy NUMERIC(10,4),
    features JSONB,
    created_at TIMESTAMPTZ DEFAULT now()
);
