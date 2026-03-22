ALTER TABLE stock_symbols
    ADD COLUMN sector_name VARCHAR(100);

ALTER TABLE stock_symbols
    ADD COLUMN annual_dividend_yield NUMERIC(8, 4);

ALTER TABLE stock_symbols
    ADD COLUMN dividend_months_csv VARCHAR(40);

CREATE INDEX idx_stock_symbols_sector_name
    ON stock_symbols(sector_name);

CREATE TABLE stock_dividend_events (
    id BIGSERIAL PRIMARY KEY,
    stock_symbol_id BIGINT NOT NULL REFERENCES stock_symbols(id),
    provider VARCHAR(30) NOT NULL,
    source_event_id VARCHAR(120),
    dedupe_key VARCHAR(64) NOT NULL,
    event_type VARCHAR(30) NOT NULL,
    declared_date DATE,
    ex_dividend_date DATE,
    record_date DATE,
    pay_date DATE,
    cash_amount NUMERIC(19, 6),
    currency_code VARCHAR(10),
    frequency_raw VARCHAR(30),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_stock_dividend_events_provider
        CHECK (provider IN ('KIS', 'POLYGON')),
    CONSTRAINT chk_stock_dividend_events_event_type
        CHECK (event_type IN ('CASH', 'SPECIAL_CASH', 'STOCK', 'OTHER')),
    CONSTRAINT chk_stock_dividend_events_cash_amount
        CHECK (cash_amount IS NULL OR cash_amount >= 0),
    CONSTRAINT chk_stock_dividend_events_has_any_date
        CHECK (
            declared_date IS NOT NULL
            OR ex_dividend_date IS NOT NULL
            OR record_date IS NOT NULL
            OR pay_date IS NOT NULL
        ),
    CONSTRAINT uk_stock_dividend_events_provider_dedupe
        UNIQUE (provider, dedupe_key)
);

CREATE INDEX idx_stock_dividend_events_symbol_pay_date
    ON stock_dividend_events(stock_symbol_id, pay_date DESC);

CREATE INDEX idx_stock_dividend_events_symbol_ex_dividend_date
    ON stock_dividend_events(stock_symbol_id, ex_dividend_date DESC);

CREATE INDEX idx_stock_dividend_events_symbol_provider
    ON stock_dividend_events(stock_symbol_id, provider);
