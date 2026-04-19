-- V20__create_pending_signals.sql
CREATE TABLE pending_signals (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    signal_id       VARCHAR(50) NOT NULL UNIQUE,
    symbol          VARCHAR(20) NOT NULL,
    direction       VARCHAR(10) NOT NULL,
    strategy_name   VARCHAR(50) NOT NULL,
    entry_price     NUMERIC(19,4) NOT NULL,
    stop_loss_price NUMERIC(19,4) NOT NULL,
    take_profit_price NUMERIC(19,4) NOT NULL,
    position_size   NUMERIC(19,8) NOT NULL,
    confidence      NUMERIC(4,3) NOT NULL,
    signal_data     JSONB       NOT NULL DEFAULT '{}',
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    expires_at      TIMESTAMPTZ NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_pending_signals_user_status ON pending_signals(user_id, status);
