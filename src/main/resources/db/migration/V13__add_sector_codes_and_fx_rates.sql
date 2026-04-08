ALTER TABLE stock_symbols
    ADD COLUMN sector_code VARCHAR(40);

CREATE INDEX idx_stock_symbols_sector_code ON stock_symbols(sector_code);

CREATE TABLE fx_rates (
    id BIGSERIAL PRIMARY KEY,
    base_currency VARCHAR(10) NOT NULL,
    quote_currency VARCHAR(10) NOT NULL,
    rate NUMERIC(19, 8) NOT NULL,
    as_of TIMESTAMP NOT NULL,
    provider VARCHAR(30) NOT NULL,
    last_synced_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_fx_rates_pair UNIQUE (base_currency, quote_currency),
    CONSTRAINT chk_fx_rates_positive_rate CHECK (rate > 0),
    CONSTRAINT chk_fx_rates_base_currency CHECK (base_currency IN ('KRW', 'USD')),
    CONSTRAINT chk_fx_rates_quote_currency CHECK (quote_currency IN ('KRW', 'USD'))
);

CREATE INDEX idx_fx_rates_pair ON fx_rates(base_currency, quote_currency);
