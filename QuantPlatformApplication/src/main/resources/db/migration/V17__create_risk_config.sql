CREATE TABLE risk_config (
    id                      BIGSERIAL PRIMARY KEY,
    user_id                 BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    risk_per_trade_pct      NUMERIC(6,4)  NOT NULL DEFAULT 0.01,
    max_effective_leverage   NUMERIC(6,2)  NOT NULL DEFAULT 5.0,
    daily_loss_halt_pct     NUMERIC(6,4)  NOT NULL DEFAULT 0.05,
    max_drawdown_pct        NUMERIC(6,4)  NOT NULL DEFAULT 0.15,
    max_concurrent_positions INTEGER       NOT NULL DEFAULT 3,
    max_stop_distance_pct   NUMERIC(6,4)  NOT NULL DEFAULT 0.02,
    min_risk_reward_ratio   NUMERIC(4,2)  NOT NULL DEFAULT 1.5,
    fee_impact_threshold    NUMERIC(6,4)  NOT NULL DEFAULT 0.20,
    execution_mode          VARCHAR(20)   NOT NULL DEFAULT 'HUMAN_IN_LOOP',
    created_at              TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_risk_config_user UNIQUE (user_id)
);

CREATE INDEX idx_risk_config_user ON risk_config(user_id);
