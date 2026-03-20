CREATE TABLE currencies
(
    code       CHAR(3) PRIMARY KEY,
    name       TEXT NOT NULL,
    symbol     TEXT NOT NULL,
    is_active  BOOLEAN     DEFAULT TRUE,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);
