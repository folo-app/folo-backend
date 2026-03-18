ALTER TABLE import_results ADD COLUMN confirmed_trade_id BIGINT;
ALTER TABLE import_results ADD COLUMN confirmed_at TIMESTAMP;
ALTER TABLE import_results
    ADD CONSTRAINT fk_import_results_confirmed_trade
    FOREIGN KEY (confirmed_trade_id) REFERENCES trades(id);

CREATE UNIQUE INDEX uq_import_results_confirmed_trade_id ON import_results(confirmed_trade_id);
CREATE INDEX idx_import_results_confirmed_at ON import_results(confirmed_at);
