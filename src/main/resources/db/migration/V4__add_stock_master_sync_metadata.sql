ALTER TABLE stock_symbols
    ADD COLUMN primary_exchange_code VARCHAR(20);

ALTER TABLE stock_symbols
    ADD COLUMN currency_code VARCHAR(10);

ALTER TABLE stock_symbols
    ADD COLUMN source_provider VARCHAR(30);

ALTER TABLE stock_symbols
    ADD COLUMN source_identifier VARCHAR(100);

ALTER TABLE stock_symbols
    ADD COLUMN last_master_synced_at TIMESTAMP;

UPDATE stock_symbols
SET primary_exchange_code = CASE market
    WHEN 'KRX' THEN 'XKRX'
    WHEN 'NASDAQ' THEN 'XNAS'
    WHEN 'NYSE' THEN 'XNYS'
    WHEN 'AMEX' THEN 'XASE'
    ELSE NULL
END
WHERE primary_exchange_code IS NULL;

UPDATE stock_symbols
SET currency_code = CASE market
    WHEN 'KRX' THEN 'KRW'
    ELSE 'USD'
END
WHERE currency_code IS NULL;

UPDATE stock_symbols
SET last_master_synced_at = CURRENT_TIMESTAMP
WHERE last_master_synced_at IS NULL;

ALTER TABLE stock_symbols
    ALTER COLUMN currency_code SET NOT NULL;

ALTER TABLE stock_symbols
    ADD CONSTRAINT chk_stock_symbols_source_provider
        CHECK (source_provider IS NULL OR source_provider IN ('KIS', 'POLYGON'));

CREATE INDEX idx_stock_symbols_provider_market
    ON stock_symbols(source_provider, market);

CREATE TABLE stock_symbol_sync_runs (
    id BIGSERIAL PRIMARY KEY,
    provider VARCHAR(30) NOT NULL,
    market VARCHAR(20) NOT NULL,
    sync_scope VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    request_cursor VARCHAR(1000),
    next_cursor VARCHAR(1000),
    fetched_count INTEGER NOT NULL DEFAULT 0,
    upserted_count INTEGER NOT NULL DEFAULT 0,
    deactivated_count INTEGER NOT NULL DEFAULT 0,
    error_message VARCHAR(2000),
    started_at TIMESTAMP NOT NULL,
    completed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_stock_symbol_sync_runs_provider CHECK (provider IN ('KIS', 'POLYGON')),
    CONSTRAINT chk_stock_symbol_sync_runs_market CHECK (market IN ('KRX', 'NASDAQ', 'NYSE', 'AMEX')),
    CONSTRAINT chk_stock_symbol_sync_runs_scope CHECK (sync_scope IN ('MASTER')),
    CONSTRAINT chk_stock_symbol_sync_runs_status CHECK (status IN ('STARTED', 'COMPLETED', 'FAILED', 'SKIPPED'))
);

CREATE INDEX idx_stock_symbol_sync_runs_provider_market_started_at
    ON stock_symbol_sync_runs(provider, market, started_at DESC);
