CREATE TABLE investment_reminders (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    stock_symbol_id BIGINT NOT NULL REFERENCES stock_symbols(id),
    amount NUMERIC(19, 4),
    day_of_month INTEGER NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    next_reminder_date DATE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_investment_reminders_day_of_month CHECK (day_of_month BETWEEN 1 AND 28)
);

CREATE TABLE import_jobs (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    import_type VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    source_file_url VARCHAR(1000),
    broker_code VARCHAR(50),
    parsed_count INTEGER DEFAULT 0,
    failed_count INTEGER DEFAULT 0,
    completed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_import_jobs_type CHECK (import_type IN ('CSV', 'OCR')),
    CONSTRAINT chk_import_jobs_status CHECK (status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED', 'CONFIRMED'))
);

CREATE TABLE import_results (
    id BIGSERIAL PRIMARY KEY,
    import_job_id BIGINT NOT NULL REFERENCES import_jobs(id),
    stock_symbol_id BIGINT REFERENCES stock_symbols(id),
    trade_type VARCHAR(10),
    quantity NUMERIC(19, 6),
    price NUMERIC(19, 6),
    traded_at TIMESTAMP,
    comment VARCHAR(300),
    visibility VARCHAR(20),
    valid BOOLEAN NOT NULL DEFAULT FALSE,
    error_message VARCHAR(500),
    selected BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_import_results_trade_type CHECK (trade_type IS NULL OR trade_type IN ('BUY', 'SELL')),
    CONSTRAINT chk_import_results_visibility CHECK (visibility IS NULL OR visibility IN ('PUBLIC', 'FRIENDS_ONLY', 'PRIVATE'))
);

CREATE INDEX idx_reminders_user_active ON investment_reminders(user_id, active);
CREATE INDEX idx_reminders_next_date ON investment_reminders(next_reminder_date);
CREATE INDEX idx_import_jobs_user_status ON import_jobs(user_id, status);
CREATE INDEX idx_import_results_job_id ON import_results(import_job_id);
