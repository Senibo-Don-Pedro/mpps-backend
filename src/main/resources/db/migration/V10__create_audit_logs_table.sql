CREATE TABLE audit_logs
(
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    action      VARCHAR(255) NOT NULL,
    status      VARCHAR(50)  NOT NULL,
    details     JSONB        NULL,
    created_at  TIMESTAMPTZ  DEFAULT NOW(),
    updated_at  TIMESTAMPTZ  DEFAULT NOW()
);

CREATE INDEX idx_audit_logs_action ON audit_logs(action);