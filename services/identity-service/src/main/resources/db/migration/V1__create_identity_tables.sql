CREATE TABLE IF NOT EXISTS identity_service.users (
    id UUID PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(100) NOT NULL,
    role VARCHAR(30) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS identity_service.refresh_tokens (
    id UUID PRIMARY KEY,
    token VARCHAR(512) NOT NULL UNIQUE,
    user_id UUID NOT NULL REFERENCES identity_service.users (id),
    expires_at TIMESTAMPTZ NOT NULL,
    revoked BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_refresh_tokens_user_id ON identity_service.refresh_tokens (user_id);
