-- V3: Create strategies table for user-defined trading strategies
-- Stores strategy configuration that maps to engine StrategyConfig

CREATE TABLE IF NOT EXISTS strategies (
    id                  BIGSERIAL PRIMARY KEY,
    name                VARCHAR(255) NOT NULL,
    symbol              VARCHAR(20)  NOT NULL,
    model_type          VARCHAR(50)  NOT NULL,   -- MOMENTUM, VOLATILITY, MACRO, CORRELATION, REGIME
    current_cash        NUMERIC(15,2) DEFAULT 100000.00,
    position_multiplier NUMERIC(8,4)  DEFAULT 1.0,
    target_risk         NUMERIC(15,2) DEFAULT 10000.00,
    active              BOOLEAN       DEFAULT TRUE,
    created_at          TIMESTAMPTZ   DEFAULT NOW(),
    updated_at          TIMESTAMPTZ   DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_strategies_symbol     ON strategies(symbol);
CREATE INDEX IF NOT EXISTS idx_strategies_model_type ON strategies(model_type);
CREATE INDEX IF NOT EXISTS idx_strategies_active     ON strategies(active);
