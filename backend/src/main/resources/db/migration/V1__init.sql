-- V1: initial schema — users + rotating refresh tokens

CREATE TABLE users (
    id            BIGSERIAL PRIMARY KEY,
    username      VARCHAR(50)  NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role          VARCHAR(20)  NOT NULL CHECK (role IN ('EDITOR', 'VIEWER')),
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE user_refresh_tokens (
    id               BIGSERIAL PRIMARY KEY,
    user_id          BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash       VARCHAR(64) NOT NULL UNIQUE,
    expires_at       TIMESTAMPTZ NOT NULL,
    revoked          BOOLEAN     NOT NULL DEFAULT FALSE,
    replaced_by_hash VARCHAR(64),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_user_refresh_tokens_user_id ON user_refresh_tokens(user_id);
