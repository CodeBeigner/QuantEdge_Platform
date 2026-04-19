CREATE TABLE trade_logs (
    id                BIGSERIAL PRIMARY KEY,
    user_id           BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    trade_id          VARCHAR(50) NOT NULL UNIQUE,
    symbol            VARCHAR(20) NOT NULL,
    direction         VARCHAR(10) NOT NULL,
    strategy_name     VARCHAR(50) NOT NULL,
    entry_price       NUMERIC(19,4) NOT NULL,
    stop_loss_price   NUMERIC(19,4) NOT NULL,
    take_profit_price NUMERIC(19,4) NOT NULL,
    position_size     NUMERIC(19,8),
    effective_leverage NUMERIC(6,2),
    confidence        NUMERIC(4,3),
    explanation       JSONB       NOT NULL DEFAULT '{}',
    outcome           JSONB,
    status            VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    execution_mode    VARCHAR(20) NOT NULL DEFAULT 'AUTONOMOUS',
    opened_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    closed_at         TIMESTAMPTZ,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_trade_logs_user ON trade_logs(user_id);
CREATE INDEX idx_trade_logs_symbol ON trade_logs(symbol);
CREATE INDEX idx_trade_logs_status ON trade_logs(status);
CREATE INDEX idx_trade_logs_strategy ON trade_logs(strategy_name);
