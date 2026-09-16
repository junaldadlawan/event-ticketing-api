-- Phase 12 (BR-ADMIN-003). No FK constraints, matching every other table in
-- this schema (see V6 migration header). Enums stored as plain VARCHAR, no
-- DB CHECK constraint - validation is Java-enum-level only.
CREATE TABLE disputes (
    id              UUID            PRIMARY KEY,
    order_id        UUID,
    ticket_id       UUID,
    raised_by       UUID            NOT NULL,
    status          VARCHAR(20)     NOT NULL,
    reason          VARCHAR(1000)   NOT NULL,
    resolution      VARCHAR(1000),
    created_at      TIMESTAMPTZ     NOT NULL,
    updated_at      TIMESTAMPTZ,
    updated_by      UUID
);

CREATE INDEX idx_disputes_raised_by ON disputes (raised_by);
CREATE INDEX idx_disputes_status ON disputes (status);
