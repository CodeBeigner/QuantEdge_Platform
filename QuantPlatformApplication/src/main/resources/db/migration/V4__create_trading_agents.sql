-- V4: Trading Agents table
-- Agents auto-execute strategies on a cron schedule.

CREATE TABLE IF NOT EXISTS trading_agents (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(100)    NOT NULL,
    strategy_id     BIGINT          NOT NULL REFERENCES strategies(id) ON DELETE CASCADE,
    cron_expression VARCHAR(50)     NOT NULL DEFAULT '0 0 9 * * MON-FRI',
    active          BOOLEAN         NOT NULL DEFAULT TRUE,
    last_run_at     TIMESTAMPTZ,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_trading_agents_strategy ON trading_agents(strategy_id);
CREATE INDEX idx_trading_agents_active   ON trading_agents(active);
