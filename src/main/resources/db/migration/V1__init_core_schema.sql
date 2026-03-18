CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    handle VARCHAR(30) NOT NULL UNIQUE,
    nickname VARCHAR(20) NOT NULL UNIQUE,
    bio VARCHAR(500),
    profile_image_url VARCHAR(1000),
    portfolio_visibility VARCHAR(20) NOT NULL,
    return_visibility VARCHAR(20) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    withdrawn_at TIMESTAMP,
    kis_app_key_encrypted VARCHAR(255),
    kis_app_secret_encrypted VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_users_portfolio_visibility CHECK (portfolio_visibility IN ('PUBLIC', 'FRIENDS_ONLY', 'PRIVATE')),
    CONSTRAINT chk_users_return_visibility CHECK (return_visibility IN ('RATE_AND_AMOUNT', 'RATE_ONLY', 'PRIVATE'))
);

CREATE TABLE user_auth_identities (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    provider VARCHAR(20) NOT NULL,
    provider_user_id VARCHAR(255) NOT NULL,
    email VARCHAR(255),
    password_hash VARCHAR(255),
    email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_auth_provider_user UNIQUE (provider, provider_user_id),
    CONSTRAINT chk_auth_provider CHECK (provider IN ('EMAIL', 'APPLE', 'GOOGLE'))
);

CREATE TABLE refresh_tokens (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    token_hash VARCHAR(500) NOT NULL UNIQUE,
    expires_at TIMESTAMP NOT NULL,
    revoked_at TIMESTAMP,
    device_id VARCHAR(255),
    device_name VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE notification_settings (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE REFERENCES users(id),
    reaction_alert BOOLEAN NOT NULL DEFAULT TRUE,
    comment_alert BOOLEAN NOT NULL DEFAULT TRUE,
    follow_alert BOOLEAN NOT NULL DEFAULT TRUE,
    reminder_alert BOOLEAN NOT NULL DEFAULT TRUE,
    nudge_alert BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE follows (
    id BIGSERIAL PRIMARY KEY,
    follower_id BIGINT NOT NULL REFERENCES users(id),
    following_id BIGINT NOT NULL REFERENCES users(id),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_follows_pair UNIQUE (follower_id, following_id),
    CONSTRAINT chk_follows_not_self CHECK (follower_id <> following_id)
);

CREATE TABLE stock_symbols (
    id BIGSERIAL PRIMARY KEY,
    market VARCHAR(20) NOT NULL,
    ticker VARCHAR(30) NOT NULL,
    name VARCHAR(100) NOT NULL,
    asset_type VARCHAR(20) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_stock_symbols_market_ticker UNIQUE (market, ticker),
    CONSTRAINT chk_stock_symbols_market CHECK (market IN ('KRX', 'NASDAQ', 'NYSE', 'AMEX')),
    CONSTRAINT chk_stock_symbols_asset_type CHECK (asset_type IN ('STOCK', 'ETF'))
);

CREATE TABLE price_snapshots (
    id BIGSERIAL PRIMARY KEY,
    stock_symbol_id BIGINT NOT NULL UNIQUE REFERENCES stock_symbols(id),
    current_price NUMERIC(19, 6) NOT NULL,
    open_price NUMERIC(19, 6),
    high_price NUMERIC(19, 6),
    low_price NUMERIC(19, 6),
    day_return NUMERIC(19, 6),
    day_return_rate NUMERIC(8, 4),
    market_updated_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE portfolios (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE REFERENCES users(id),
    total_invested NUMERIC(19, 4) NOT NULL DEFAULT 0,
    total_value NUMERIC(19, 4) NOT NULL DEFAULT 0,
    total_return_amount NUMERIC(19, 4) NOT NULL DEFAULT 0,
    total_return_rate NUMERIC(8, 4) NOT NULL DEFAULT 0,
    day_return_amount NUMERIC(19, 4) NOT NULL DEFAULT 0,
    day_return_rate NUMERIC(8, 4) NOT NULL DEFAULT 0,
    display_currency VARCHAR(10) NOT NULL DEFAULT 'KRW',
    synced_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE holdings (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    stock_symbol_id BIGINT NOT NULL REFERENCES stock_symbols(id),
    quantity NUMERIC(19, 6) NOT NULL DEFAULT 0,
    avg_price NUMERIC(19, 6) NOT NULL DEFAULT 0,
    total_invested NUMERIC(19, 4) NOT NULL DEFAULT 0,
    total_value NUMERIC(19, 4) NOT NULL DEFAULT 0,
    return_amount NUMERIC(19, 4) NOT NULL DEFAULT 0,
    return_rate NUMERIC(8, 4) NOT NULL DEFAULT 0,
    weight NUMERIC(8, 4) NOT NULL DEFAULT 0,
    first_bought_date DATE,
    last_trade_date DATE,
    calculated_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_holdings_user_symbol UNIQUE (user_id, stock_symbol_id)
);

CREATE TABLE trades (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    stock_symbol_id BIGINT NOT NULL REFERENCES stock_symbols(id),
    trade_type VARCHAR(10) NOT NULL,
    quantity NUMERIC(19, 6) NOT NULL,
    price NUMERIC(19, 6) NOT NULL,
    total_amount NUMERIC(19, 4) NOT NULL,
    comment VARCHAR(300),
    visibility VARCHAR(20) NOT NULL,
    traded_at TIMESTAMP NOT NULL,
    source VARCHAR(30) NOT NULL,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_trades_trade_type CHECK (trade_type IN ('BUY', 'SELL')),
    CONSTRAINT chk_trades_visibility CHECK (visibility IN ('PUBLIC', 'FRIENDS_ONLY', 'PRIVATE')),
    CONSTRAINT chk_trades_source CHECK (source IN ('MANUAL', 'CSV_IMPORT', 'OCR_IMPORT', 'INITIAL_SETUP', 'KIS_SYNC', 'HOLDING_ADJUSTMENT')),
    CONSTRAINT chk_trades_quantity CHECK (quantity > 0),
    CONSTRAINT chk_trades_price CHECK (price >= 0),
    CONSTRAINT chk_trades_total_amount CHECK (total_amount >= 0)
);

CREATE TABLE reactions (
    id BIGSERIAL PRIMARY KEY,
    trade_id BIGINT NOT NULL REFERENCES trades(id),
    user_id BIGINT NOT NULL REFERENCES users(id),
    emoji VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_reactions_trade_user UNIQUE (trade_id, user_id),
    CONSTRAINT chk_reactions_emoji CHECK (emoji IN ('FIRE', 'EYES', 'DIAMOND', 'CLAP', 'ROCKET'))
);

CREATE TABLE comments (
    id BIGSERIAL PRIMARY KEY,
    trade_id BIGINT NOT NULL REFERENCES trades(id),
    user_id BIGINT NOT NULL REFERENCES users(id),
    content VARCHAR(500) NOT NULL,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE notifications (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    type VARCHAR(20) NOT NULL,
    actor_user_id BIGINT REFERENCES users(id),
    target_type VARCHAR(20) NOT NULL,
    target_id BIGINT NOT NULL,
    message VARCHAR(500) NOT NULL,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_notifications_type CHECK (type IN ('FOLLOW', 'REACTION', 'COMMENT', 'REMINDER', 'NUDGE')),
    CONSTRAINT chk_notifications_target_type CHECK (target_type IN ('USER', 'TRADE', 'COMMENT', 'REMINDER', 'TODO', 'PORTFOLIO'))
);

CREATE INDEX idx_auth_user_id ON user_auth_identities(user_id);
CREATE INDEX idx_auth_email ON user_auth_identities(email);
CREATE INDEX idx_refresh_user_id ON refresh_tokens(user_id);
CREATE INDEX idx_refresh_expires_at ON refresh_tokens(expires_at);
CREATE INDEX idx_follows_follower_id ON follows(follower_id);
CREATE INDEX idx_follows_following_id ON follows(following_id);
CREATE INDEX idx_stock_symbols_name ON stock_symbols(name);
CREATE INDEX idx_stock_symbols_ticker ON stock_symbols(ticker);
CREATE INDEX idx_holdings_user_id ON holdings(user_id);
CREATE INDEX idx_holdings_symbol_id ON holdings(stock_symbol_id);
CREATE INDEX idx_trades_user_created_at ON trades(user_id, created_at DESC);
CREATE INDEX idx_trades_user_traded_at ON trades(user_id, traded_at DESC);
CREATE INDEX idx_trades_symbol_id ON trades(stock_symbol_id);
CREATE INDEX idx_trades_visibility ON trades(visibility);
CREATE INDEX idx_comments_trade_created_at ON comments(trade_id, created_at ASC);
CREATE INDEX idx_comments_user_id ON comments(user_id);
CREATE INDEX idx_notifications_user_created_at ON notifications(user_id, created_at DESC);
CREATE INDEX idx_notifications_user_is_read ON notifications(user_id, is_read);

INSERT INTO stock_symbols (market, ticker, name, asset_type, active)
VALUES
    ('KRX', '005930', '삼성전자', 'STOCK', TRUE),
    ('KRX', '000660', 'SK하이닉스', 'STOCK', TRUE),
    ('NASDAQ', 'AAPL', 'Apple Inc.', 'STOCK', TRUE),
    ('NASDAQ', 'NVDA', 'NVIDIA Corporation', 'STOCK', TRUE),
    ('NYSE', 'VOO', 'Vanguard S&P 500 ETF', 'ETF', TRUE);

INSERT INTO price_snapshots (stock_symbol_id, current_price, open_price, high_price, low_price, day_return, day_return_rate, market_updated_at)
SELECT id, 74200, 74500, 74800, 73900, -400, -0.5400, CURRENT_TIMESTAMP FROM stock_symbols WHERE market = 'KRX' AND ticker = '005930';

INSERT INTO price_snapshots (stock_symbol_id, current_price, open_price, high_price, low_price, day_return, day_return_rate, market_updated_at)
SELECT id, 168000, 170000, 171500, 167000, -2500, -1.4600, CURRENT_TIMESTAMP FROM stock_symbols WHERE market = 'KRX' AND ticker = '000660';

INSERT INTO price_snapshots (stock_symbol_id, current_price, open_price, high_price, low_price, day_return, day_return_rate, market_updated_at)
SELECT id, 189.30, 187.00, 190.25, 186.80, 2.30, 1.2300, CURRENT_TIMESTAMP FROM stock_symbols WHERE market = 'NASDAQ' AND ticker = 'AAPL';

INSERT INTO price_snapshots (stock_symbol_id, current_price, open_price, high_price, low_price, day_return, day_return_rate, market_updated_at)
SELECT id, 122.45, 120.00, 123.20, 119.50, 3.20, 2.6800, CURRENT_TIMESTAMP FROM stock_symbols WHERE market = 'NASDAQ' AND ticker = 'NVDA';

INSERT INTO price_snapshots (stock_symbol_id, current_price, open_price, high_price, low_price, day_return, day_return_rate, market_updated_at)
SELECT id, 555.10, 552.00, 556.30, 551.20, 1.40, 0.2500, CURRENT_TIMESTAMP FROM stock_symbols WHERE market = 'NYSE' AND ticker = 'VOO';
