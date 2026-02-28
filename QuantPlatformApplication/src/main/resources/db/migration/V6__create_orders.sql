-- Orders table for OMS
CREATE TABLE IF NOT EXISTS orders (
    id BIGSERIAL PRIMARY KEY,
    symbol VARCHAR(20) NOT NULL,
    side VARCHAR(10) NOT NULL,          -- BUY, SELL
    order_type VARCHAR(20) NOT NULL,    -- MARKET, LIMIT
    quantity INTEGER NOT NULL,
    price NUMERIC(19,4),
    filled_price NUMERIC(19,4),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',  -- PENDING, FILLED, CANCELLED, REJECTED
    strategy_id BIGINT REFERENCES strategies(id),
    slippage NUMERIC(10,6),
    created_at TIMESTAMPTZ DEFAULT now(),
    updated_at TIMESTAMPTZ DEFAULT now()
);
