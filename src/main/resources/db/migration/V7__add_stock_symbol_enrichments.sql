ALTER TABLE stock_symbol_sync_runs
    DROP CONSTRAINT chk_stock_symbol_sync_runs_scope;

ALTER TABLE stock_symbol_sync_runs
    ADD CONSTRAINT chk_stock_symbol_sync_runs_scope
        CHECK (sync_scope IN ('MASTER', 'DIVIDEND', 'ENRICHMENT'));

CREATE TABLE stock_symbol_enrichments (
    id BIGSERIAL PRIMARY KEY,
    stock_symbol_id BIGINT NOT NULL REFERENCES stock_symbols(id),
    provider VARCHAR(30) NOT NULL,
    sector_name_raw VARCHAR(120),
    industry_name_raw VARCHAR(160),
    classification_scheme VARCHAR(30) NOT NULL,
    source_payload_version VARCHAR(60),
    last_enriched_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_stock_symbol_enrichments_symbol_provider
        UNIQUE (stock_symbol_id, provider),
    CONSTRAINT chk_stock_symbol_enrichments_provider
        CHECK (provider IN ('KIS', 'POLYGON')),
    CONSTRAINT chk_stock_symbol_enrichments_scheme
        CHECK (classification_scheme IN ('SIC'))
);

CREATE INDEX idx_stock_symbol_enrichments_symbol
    ON stock_symbol_enrichments(stock_symbol_id);

CREATE INDEX idx_stock_symbol_enrichments_provider_last_enriched_at
    ON stock_symbol_enrichments(provider, last_enriched_at DESC);
