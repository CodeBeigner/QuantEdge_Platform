-- Portfolio positions table
CREATE TABLE IF NOT EXISTS portfolio_positions (
    id BIGSERIAL PRIMARY KEY,
    symbol VARCHAR(20) NOT NULL,
    quantity INTEGER NOT NULL DEFAULT 0,
    avg_cost NUMERIC(19,4) NOT NULL DEFAULT 0,
    current_price NUMERIC(19,4),
    unrealized_pnl NUMERIC(19,4),
    realized_pnl NUMERIC(19,4) DEFAULT 0,
    weight NUMERIC(10,6),
    updated_at TIMESTAMPTZ DEFAULT now(),
    UNIQUE(symbol)
);
