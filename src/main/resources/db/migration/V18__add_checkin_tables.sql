-- Phase 10 — Check-in & Scanning.
--
-- No FK constraints anywhere, matching every other table in this schema
-- (see V6 through V17).

-- Managed-resource-ish tier per the ERD: created_at/updated_at/updated_by,
-- deliberately no created_by (the ERD doesn't list one for this entity) and
-- no deleted_at (a check-in config is reconfigured in place, never
-- soft-deleted). Own generated id + a unique event_id column rather than
-- the ERD's literal "event_id as primary key" depiction - same deviation
-- already established for ResalePolicy/RefundPolicy.
CREATE TABLE check_in_configs (
    id                              UUID            PRIMARY KEY,
    event_id                        UUID            NOT NULL,
    mode                            VARCHAR(20)     NOT NULL,
    offline_fallback_expiry_seconds INT             NOT NULL DEFAULT 300,
    created_at                      TIMESTAMPTZ     NOT NULL,
    updated_at                      TIMESTAMPTZ,
    updated_by                      VARCHAR(255)
);

CREATE UNIQUE INDEX uq_check_in_configs_event_id ON check_in_configs (event_id);

-- Transactional tier per the ERD: created_at/created_by/updated_at/
-- updated_by, no deleted_at (deactivation is the "revoked" status, not a
-- soft delete). No credential column: a device's credential is an
-- HMAC-signed token re-derived from its own id (see
-- ScannerDeviceCredentialService), the same "deliberately re-derivable, not
-- separately stored" design as TicketCredentialService (Phase 6a) - nothing
-- to store or leak.
CREATE TABLE scanner_devices (
    id              UUID            PRIMARY KEY,
    event_id        UUID            NOT NULL,
    device_label    VARCHAR(255)    NOT NULL,
    status          VARCHAR(20)     NOT NULL,
    created_at      TIMESTAMPTZ     NOT NULL,
    created_by      VARCHAR(255)    NOT NULL,
    updated_at      TIMESTAMPTZ,
    updated_by      VARCHAR(255)
);

CREATE INDEX idx_scanner_devices_event_id ON scanner_devices (event_id);

-- Immutable, append-only tier per the ERD: a single creation timestamp
-- (scanned_at) and nothing else.
CREATE TABLE check_in_records (
    id              UUID            PRIMARY KEY,
    ticket_id       UUID            NOT NULL,
    source_type     VARCHAR(20)     NOT NULL,
    source_id       UUID            NOT NULL,
    result          VARCHAR(20)     NOT NULL,
    scanned_at      TIMESTAMPTZ     NOT NULL
);

CREATE INDEX idx_check_in_records_ticket_id ON check_in_records (ticket_id);

-- Transactional tier per the ERD: captured_at is client-supplied (when the
-- offline device captured the scan, per openapi's submitFallbackScans
-- request body), synced_at/reconciled_ticket_id are server-set once
-- reconciliation happens (always immediately in this implementation - the
-- upload call itself proves connectivity, so there's no genuinely deferred
-- "pending reconciliation" state to model).
CREATE TABLE fallback_scan_records (
    id                      UUID            PRIMARY KEY,
    event_id                UUID            NOT NULL,
    raw_credential          VARCHAR(500)    NOT NULL,
    captured_at             TIMESTAMPTZ     NOT NULL,
    synced_at               TIMESTAMPTZ,
    reconciled_ticket_id    UUID
);

CREATE INDEX idx_fallback_scan_records_event_id ON fallback_scan_records (event_id);
