ALTER TABLE users
    ADD COLUMN kis_access_token_encrypted VARCHAR(3000),
    ADD COLUMN kis_refresh_token_encrypted VARCHAR(3000),
    ADD COLUMN kis_personal_secret_key_encrypted VARCHAR(500),
    ADD COLUMN kis_account_number_encrypted VARCHAR(255),
    ADD COLUMN kis_account_product_code VARCHAR(20),
    ADD COLUMN kis_access_token_expires_at TIMESTAMP,
    ADD COLUMN kis_refresh_token_expires_at TIMESTAMP,
    ADD COLUMN kis_connected_at TIMESTAMP;
