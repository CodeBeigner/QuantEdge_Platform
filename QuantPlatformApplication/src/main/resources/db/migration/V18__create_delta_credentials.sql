CREATE TABLE delta_credentials (
    id                BIGSERIAL PRIMARY KEY,
    user_id           BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    api_key_encrypted VARCHAR(512) NOT NULL,
    api_secret_encrypted VARCHAR(512) NOT NULL,
    is_testnet        BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_delta_cred_user_env UNIQUE (user_id, is_testnet)
);

CREATE INDEX idx_delta_cred_user ON delta_credentials(user_id);
