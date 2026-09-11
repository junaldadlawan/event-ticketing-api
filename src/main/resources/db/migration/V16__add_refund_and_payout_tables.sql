-- Phase 8 — Refunds & Payouts.
--
-- No FK constraints anywhere, matching every other table in this schema
-- (see V6/V7/V8/V9/V10/V11/V12/V13/V14/V15).

-- Managed-resource tier per the ERD's audit-column policy: full audit set,
-- same as resale_policies/ticket_templates. Own generated id + a unique
-- event_id column rather than the ERD's literal "event_id as primary key"
-- depiction - same deviation already established for resale_policies (no
-- other table in this schema uses a borrowed/shared primary key).
CREATE TABLE refund_policies (
    id                  UUID            PRIMARY KEY,
    event_id            UUID            NOT NULL,
    rule_type           VARCHAR(30)     NOT NULL,
    days_before_event   INT,
    custom_terms        VARCHAR(2000),
    created_by          VARCHAR(255)    NOT NULL,
    created_at          TIMESTAMPTZ     NOT NULL,
    updated_by          VARCHAR(255),
    updated_at          TIMESTAMPTZ,
    deleted_at          TIMESTAMPTZ
);

-- One policy per event ("Event ||--o| RefundPolicy: has").
CREATE UNIQUE INDEX uq_refund_policies_event_id ON refund_policies (event_id);

-- Transactional/status-bearing tier per the ERD's audit-column policy:
-- created_at/updated_at only, no created_by/updated_by/deleted_at (same
-- tier as Order/Ticket/Payment/ResaleListing). initiated_by is a business
-- fact (BR-PAY-002: who triggered this refund - organizer/admin, or the
-- system itself on event cancellation), not an audit column.
CREATE TABLE refunds (
    id                  UUID            PRIMARY KEY,
    order_id            UUID            NOT NULL,
    amount_amount       BIGINT          NOT NULL,
    amount_currency     VARCHAR(3)      NOT NULL,
    reason              VARCHAR(1000)   NOT NULL,
    initiated_by        UUID            NOT NULL,
    status              VARCHAR(20)     NOT NULL,
    created_at          TIMESTAMPTZ     NOT NULL,
    updated_at          TIMESTAMPTZ
);

CREATE INDEX idx_refunds_order_id ON refunds (order_id);

-- Transactional/status-bearing tier, same as Refund. Read-only from the API
-- per openapi.yaml (system-generated on a schedule - no creation endpoint
-- exists in this phase), so no created_by either.
CREATE TABLE payouts (
    id                  UUID            PRIMARY KEY,
    organization_id     UUID            NOT NULL,
    gross_amount        BIGINT          NOT NULL,
    gross_currency      VARCHAR(3)      NOT NULL,
    fees_amount         BIGINT          NOT NULL,
    fees_currency       VARCHAR(3)      NOT NULL,
    net_amount          BIGINT          NOT NULL,
    net_currency        VARCHAR(3)      NOT NULL,
    period_start        DATE            NOT NULL,
    period_end          DATE            NOT NULL,
    status              VARCHAR(20)     NOT NULL,
    created_at          TIMESTAMPTZ     NOT NULL,
    updated_at          TIMESTAMPTZ
);

CREATE INDEX idx_payouts_organization_id ON payouts (organization_id);
