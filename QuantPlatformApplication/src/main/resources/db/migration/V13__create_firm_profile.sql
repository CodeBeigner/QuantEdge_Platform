CREATE TABLE IF NOT EXISTS firm_profiles (
    id BIGSERIAL PRIMARY KEY,
    firm_name VARCHAR(100) NOT NULL,
    firm_type VARCHAR(50) NOT NULL,
    initial_capital NUMERIC(20,2),
    risk_appetite VARCHAR(20) DEFAULT 'MODERATE',
    setup_complete BOOLEAN DEFAULT false,
    owner_user_id BIGINT,
    created_at TIMESTAMPTZ DEFAULT now(),
    updated_at TIMESTAMPTZ DEFAULT now()
);
