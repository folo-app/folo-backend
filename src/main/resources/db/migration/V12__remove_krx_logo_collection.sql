DELETE FROM stock_symbol_sync_runs
WHERE sync_scope = 'BRANDING';

DROP TABLE IF EXISTS stock_brand_assets;

ALTER TABLE stock_symbol_sync_runs
    DROP CONSTRAINT chk_stock_symbol_sync_runs_scope;

ALTER TABLE stock_symbol_sync_runs
    ADD CONSTRAINT chk_stock_symbol_sync_runs_scope
        CHECK (sync_scope IN ('MASTER', 'DIVIDEND', 'ENRICHMENT', 'ISSUER_PROFILE'));
