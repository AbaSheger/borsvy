-- V1: Baseline schema (existing tables before Phase 1)

CREATE TABLE IF NOT EXISTS stock_analysis (
    symbol VARCHAR(20) PRIMARY KEY,
    ai_analysis TEXT,
    technical TEXT,
    fundamental TEXT,
    sentiment TEXT,
    recommendation TEXT,
    news_sentiment TEXT,
    timestamp TIMESTAMP
);

CREATE TABLE IF NOT EXISTS favorites (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT,
    symbol VARCHAR(20) NOT NULL,
    name VARCHAR(255),
    price DOUBLE PRECISION NOT NULL DEFAULT 0,
    change DOUBLE PRECISION NOT NULL DEFAULT 0,
    change_percent DOUBLE PRECISION NOT NULL DEFAULT 0,
    added_at TIMESTAMP,
    version BIGINT DEFAULT 0,
    CONSTRAINT uq_favorites_user_symbol UNIQUE (user_id, symbol)
);
