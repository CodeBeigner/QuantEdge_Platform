-- Alerts table
CREATE TABLE IF NOT EXISTS alerts (
    id BIGSERIAL PRIMARY KEY,
    alert_type VARCHAR(30) NOT NULL,    -- DRAWDOWN, POSITION_LIMIT, VAR_BREACH, SIGNAL
    severity VARCHAR(10) NOT NULL,       -- INFO, WARNING, CRITICAL
    message TEXT NOT NULL,
    symbol VARCHAR(20),
    acknowledged BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMPTZ DEFAULT now()
);
