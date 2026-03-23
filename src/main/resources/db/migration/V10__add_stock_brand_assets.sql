ALTER TABLE stock_symbol_sync_runs
    DROP CONSTRAINT chk_stock_symbol_sync_runs_scope;

ALTER TABLE stock_symbol_sync_runs
    ADD CONSTRAINT chk_stock_symbol_sync_runs_scope
        CHECK (sync_scope IN ('MASTER', 'DIVIDEND', 'ENRICHMENT', 'ISSUER_PROFILE', 'BRANDING'));

CREATE TABLE stock_brand_assets (
    id BIGSERIAL PRIMARY KEY,
    stock_symbol_id BIGINT NOT NULL REFERENCES stock_symbols(id),
    provider VARCHAR(30) NOT NULL,
    source_type VARCHAR(20) NOT NULL,
    source_url VARCHAR(1000),
    storage_path VARCHAR(500) NOT NULL,
    public_url VARCHAR(500) NOT NULL,
    content_type VARCHAR(100),
    last_synced_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_stock_brand_assets_symbol
        UNIQUE (stock_symbol_id),
    CONSTRAINT chk_stock_brand_assets_provider
        CHECK (provider IN ('OPENDART')),
    CONSTRAINT chk_stock_brand_assets_source_type
        CHECK (source_type IN ('FAVICON', 'OG_IMAGE', 'MANUAL'))
);

CREATE INDEX idx_stock_brand_assets_stock_symbol_id
    ON stock_brand_assets(stock_symbol_id);

CREATE INDEX idx_stock_brand_assets_last_synced_at
    ON stock_brand_assets(last_synced_at DESC);
