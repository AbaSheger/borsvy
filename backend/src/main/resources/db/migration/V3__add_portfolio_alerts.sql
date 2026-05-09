-- V3: Persist user portfolio holdings and price alerts

CREATE TABLE IF NOT EXISTS portfolio_holdings (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    symbol VARCHAR(16) NOT NULL,
    shares DOUBLE PRECISION NOT NULL,
    buy_price DOUBLE PRECISION NOT NULL,
    buy_date DATE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS price_alerts (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    symbol VARCHAR(16) NOT NULL,
    target_price DOUBLE PRECISION NOT NULL,
    direction VARCHAR(16) NOT NULL DEFAULT 'above',
    active BOOLEAN NOT NULL DEFAULT TRUE,
    triggered BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    triggered_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_portfolio_holdings_user ON portfolio_holdings(user_id);
CREATE INDEX IF NOT EXISTS idx_price_alerts_user ON price_alerts(user_id);
CREATE INDEX IF NOT EXISTS idx_price_alerts_active ON price_alerts(user_id, active, triggered);
