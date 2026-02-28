-- V5: Backtest Results table
-- Stores output of historical backtests including equity curve and metrics.

CREATE TABLE IF NOT EXISTS backtest_results (
    id              BIGSERIAL PRIMARY KEY,
    strategy_id     BIGINT          NOT NULL REFERENCES strategies(id) ON DELETE CASCADE,
    start_date      DATE            NOT NULL,
    end_date        DATE            NOT NULL,
    initial_capital NUMERIC(15,2)   NOT NULL,
    final_capital   NUMERIC(15,2)   NOT NULL,
    total_return    NUMERIC(10,4),
    sharpe_ratio    NUMERIC(8,4),
    max_drawdown    NUMERIC(8,4),
    win_rate        NUMERIC(5,2),
    total_trades    INTEGER         NOT NULL DEFAULT 0,
    equity_curve    JSONB,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_backtest_strategy ON backtest_results(strategy_id);
