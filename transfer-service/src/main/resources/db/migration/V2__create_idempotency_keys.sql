CREATE TABLE idempotency_keys (
    idempotency_key VARCHAR(64) NOT NULL,
    request_hash    VARCHAR(64) NOT NULL,
    transfer_id     VARCHAR(36),
    response_status INTEGER,
    response_body   TEXT,
    created_at      TIMESTAMPTZ NOT NULL
);

CREATE INDEX idempotency_keys_key_idx ON idempotency_keys (idempotency_key);
