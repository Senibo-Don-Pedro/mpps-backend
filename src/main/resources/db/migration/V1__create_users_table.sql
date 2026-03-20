CREATE TABLE users
(
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email         TEXT NOT NULL UNIQUE,
    password_hash TEXT,
    phone_number  TEXT UNIQUE,
    first_name    TEXT,
    last_name     TEXT,
    is_active     BOOLEAN          DEFAULT TRUE,
    is_verified   BOOLEAN          DEFAULT FALSE,
    created_at    TIMESTAMPTZ      DEFAULT NOW(),
    updated_at    TIMESTAMPTZ      DEFAULT NOW()
);
