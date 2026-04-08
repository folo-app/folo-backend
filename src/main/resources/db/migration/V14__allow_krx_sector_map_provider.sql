ALTER TABLE stock_symbol_sync_runs
    DROP CONSTRAINT chk_stock_symbol_sync_runs_provider;

ALTER TABLE stock_symbol_sync_runs
    ADD CONSTRAINT chk_stock_symbol_sync_runs_provider
        CHECK (provider IN ('KIS', 'POLYGON', 'OPENDART', 'KRX_SECTOR_MAP'));

ALTER TABLE stock_symbol_enrichments
    DROP CONSTRAINT chk_stock_symbol_enrichments_provider;

ALTER TABLE stock_symbol_enrichments
    ADD CONSTRAINT chk_stock_symbol_enrichments_provider
        CHECK (provider IN ('KIS', 'POLYGON', 'KRX_SECTOR_MAP'));

ALTER TABLE stock_symbol_enrichments
    DROP CONSTRAINT chk_stock_symbol_enrichments_scheme;

ALTER TABLE stock_symbol_enrichments
    ADD CONSTRAINT chk_stock_symbol_enrichments_scheme
        CHECK (classification_scheme IN ('SIC', 'KIS_MASTER', 'KRX_SECTOR_MAP'));
