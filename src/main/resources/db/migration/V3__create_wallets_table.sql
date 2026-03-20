CREATE TABLE wallets
(
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    balance       NUMERIC(19, 4)   DEFAULT 0.00,
    currency_code CHAR(3) NOT NULL REFERENCES currencies (code) ON DELETE RESTRICT,
    user_id       UUID    NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    created_at    TIMESTAMPTZ      DEFAULT NOW(),
    updated_at    TIMESTAMPTZ      DEFAULT NOW()

);
