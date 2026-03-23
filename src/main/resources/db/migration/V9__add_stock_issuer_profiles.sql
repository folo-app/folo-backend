ALTER TABLE stock_symbol_sync_runs
    DROP CONSTRAINT chk_stock_symbol_sync_runs_provider;

ALTER TABLE stock_symbol_sync_runs
    ADD CONSTRAINT chk_stock_symbol_sync_runs_provider
        CHECK (provider IN ('KIS', 'POLYGON', 'OPENDART'));

ALTER TABLE stock_symbol_sync_runs
    DROP CONSTRAINT chk_stock_symbol_sync_runs_scope;

ALTER TABLE stock_symbol_sync_runs
    ADD CONSTRAINT chk_stock_symbol_sync_runs_scope
        CHECK (sync_scope IN ('MASTER', 'DIVIDEND', 'ENRICHMENT', 'ISSUER_PROFILE'));

ALTER TABLE stock_symbol_enrichments
    DROP CONSTRAINT chk_stock_symbol_enrichments_scheme;

ALTER TABLE stock_symbol_enrichments
    ADD CONSTRAINT chk_stock_symbol_enrichments_scheme
        CHECK (classification_scheme IN ('SIC', 'KIS_MASTER'));

CREATE TABLE stock_issuer_profiles (
    id BIGSERIAL PRIMARY KEY,
    stock_symbol_id BIGINT NOT NULL REFERENCES stock_symbols(id),
    provider VARCHAR(30) NOT NULL,
    corp_code VARCHAR(8) NOT NULL,
    corp_name VARCHAR(200) NOT NULL,
    stock_code VARCHAR(12) NOT NULL,
    corp_cls VARCHAR(10),
    hm_url VARCHAR(500),
    ir_url VARCHAR(500),
    induty_code VARCHAR(20),
    source_payload_version VARCHAR(60),
    last_synced_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_stock_issuer_profiles_symbol_provider
        UNIQUE (stock_symbol_id, provider),
    CONSTRAINT uk_stock_issuer_profiles_provider_corp_code
        UNIQUE (provider, corp_code),
    CONSTRAINT chk_stock_issuer_profiles_provider
        CHECK (provider IN ('OPENDART'))
);

CREATE INDEX idx_stock_issuer_profiles_stock_symbol_id
    ON stock_issuer_profiles(stock_symbol_id);

CREATE INDEX idx_stock_issuer_profiles_induty_code
    ON stock_issuer_profiles(induty_code);

CREATE INDEX idx_stock_issuer_profiles_last_synced_at
    ON stock_issuer_profiles(last_synced_at DESC);
