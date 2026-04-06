CREATE TYPE webhook_status AS ENUM ('PENDING', 'SUCCESS', 'FAILED', 'PERMANENTLY_FAILED');

CREATE TABLE webhook_deliveries
(
    id              UUID PRIMARY KEY        DEFAULT gen_random_uuid(),
    transaction_id  UUID           NOT NULL REFERENCES transactions (id) ON DELETE RESTRICT,
    url             TEXT           NOT NULL,
    payload         JSONB          NULL,
    status          webhook_status NOT NULL DEFAULT 'PENDING',
    attempt_count   INTEGER        NOT NULL DEFAULT 0,
    last_attempt_at TIMESTAMPTZ    NULL,
    created_at      TIMESTAMPTZ             DEFAULT NOW(),
    updated_at      TIMESTAMPTZ    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_webhook_deliveries_transaction_id ON webhook_deliveries (transaction_id);
CREATE INDEX idx_webhook_deliveries_status ON webhook_deliveries (status);
