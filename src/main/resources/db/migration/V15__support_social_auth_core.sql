ALTER TABLE user_auth_identities
    DROP CONSTRAINT chk_auth_provider;

ALTER TABLE user_auth_identities
    ADD CONSTRAINT chk_auth_provider CHECK (provider IN ('EMAIL', 'APPLE', 'GOOGLE', 'KAKAO', 'NAVER'));

ALTER TABLE refresh_tokens
    ADD COLUMN auth_identity_id BIGINT;

UPDATE refresh_tokens rt
SET auth_identity_id = uai.id
FROM user_auth_identities uai
WHERE uai.user_id = rt.user_id
  AND uai.provider = 'EMAIL'
  AND rt.auth_identity_id IS NULL;

ALTER TABLE refresh_tokens
    ADD CONSTRAINT fk_refresh_tokens_auth_identity
        FOREIGN KEY (auth_identity_id) REFERENCES user_auth_identities(id);

CREATE INDEX idx_refresh_auth_identity_id ON refresh_tokens(auth_identity_id);
