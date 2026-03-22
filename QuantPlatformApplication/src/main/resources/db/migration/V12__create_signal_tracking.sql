CREATE TABLE IF NOT EXISTS signal_predictions (
    id BIGSERIAL PRIMARY KEY,
    strategy_id BIGINT NOT NULL,
    symbol VARCHAR(20) NOT NULL,
    predicted_direction INTEGER,
    predicted_confidence NUMERIC(10,4),
    actual_return NUMERIC(10,6),
    signal_correct BOOLEAN,
    created_at TIMESTAMPTZ DEFAULT now(),
    resolved_at TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS idx_signal_predictions_strategy ON signal_predictions(strategy_id, created_at DESC);
