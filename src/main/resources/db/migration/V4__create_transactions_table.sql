CREATE TYPE transaction_type AS ENUM ('CREDIT', 'DEBIT', 'TRANSFER');
CREATE TYPE transaction_status AS ENUM ('PENDING', 'SUCCESS', 'FAILED', 'REVERSED');

CREATE TABLE transactions
(
    id              UUID PRIMARY KEY            DEFAULT gen_random_uuid(),
    wallet_id       UUID               NOT NULL REFERENCES wallets (id) ON DELETE RESTRICT,
    type            transaction_type   NOT NULL,
    status          transaction_status NOT NULL DEFAULT 'PENDING',
    amount          NUMERIC(19, 4)     NOT NULL CHECK (amount > 0),
    reference       TEXT,
    idempotency_key UUID               NOT NULL UNIQUE,
    metadata        JSONB,
    created_at      TIMESTAMPTZ                 DEFAULT NOW(),
    updated_at      TIMESTAMPTZ                 DEFAULT NOW()
);
