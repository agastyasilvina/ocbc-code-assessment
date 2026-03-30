CREATE TABLE transfers (
    transfer_id         VARCHAR(36)    PRIMARY KEY,
    status              VARCHAR(20)    NOT NULL,
    reason_code         VARCHAR(40),
    source_account      VARCHAR(10)    NOT NULL,
    destination_account VARCHAR(10)    NOT NULL,
    debit_amount        NUMERIC(19, 2) NOT NULL,
    debit_currency      VARCHAR(3)     NOT NULL,
    credit_amount       NUMERIC(19, 2),
    credit_currency     VARCHAR(3),
    fx_rate             NUMERIC(19, 8),
    core_txn_id         VARCHAR(40),
    description         VARCHAR(140),
    created_at          TIMESTAMPTZ    NOT NULL,
    updated_at          TIMESTAMPTZ    NOT NULL
);

CREATE INDEX transfers_status_idx ON transfers (status);

CREATE TABLE audit_events (
    id          BIGSERIAL   PRIMARY KEY,
    transfer_id VARCHAR(36) NOT NULL,
    event_type  VARCHAR(40) NOT NULL,
    detail      TEXT,
    created_at  TIMESTAMPTZ NOT NULL
);
