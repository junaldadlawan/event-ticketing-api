-- Phase 4 — Ticket Types & Inventory.
--
-- ticket_types, seat_maps, seats are brand new tables (confirmed via
-- `docker exec postgresql psql -c "\d ticket_types"` etc. against the local
-- dev DB before writing this migration — none of these three tables, nor
-- any ddl-auto-created columns for them, exist yet), so no defensive
-- IF EXISTS/SET NOT NULL guarding is needed here, unlike V8.
--
-- No FK constraints, matching every other table in this schema (see V6/V7/V8).

CREATE TABLE ticket_types (
    id                  UUID            PRIMARY KEY,
    event_id            UUID            NOT NULL,
    name                VARCHAR(200)    NOT NULL,
    kind                VARCHAR(20)     NOT NULL,
    price_amount        BIGINT          NOT NULL,
    price_currency      VARCHAR(3)      NOT NULL,
    quantity_total      INT             NOT NULL,
    quantity_available  INT             NOT NULL,
    sale_start_at       TIMESTAMPTZ     NOT NULL,
    sale_end_at         TIMESTAMPTZ     NOT NULL,
    max_per_order       INT             NOT NULL DEFAULT 10,
    created_by          VARCHAR(255)    NOT NULL,
    created_at          TIMESTAMPTZ     NOT NULL,
    updated_by          VARCHAR(255),
    updated_at          TIMESTAMPTZ,
    deleted_at          TIMESTAMPTZ
);

CREATE TABLE seat_maps (
    id                  UUID            PRIMARY KEY,
    event_id            UUID            NOT NULL,
    created_by          VARCHAR(255)    NOT NULL,
    created_at          TIMESTAMPTZ     NOT NULL,
    updated_by          VARCHAR(255),
    updated_at          TIMESTAMPTZ,
    deleted_at          TIMESTAMPTZ
);

-- Transactional/status-bearing tier per the ERD's audit-column policy:
-- created_at/updated_at only, no created_by/updated_by/deleted_at.
CREATE TABLE seats (
    id                  UUID            PRIMARY KEY,
    seat_map_id         UUID            NOT NULL,
    section             VARCHAR(100)    NOT NULL,
    row                 VARCHAR(20)     NOT NULL,
    seat_number         VARCHAR(20)     NOT NULL,
    status              VARCHAR(20)     NOT NULL DEFAULT 'AVAILABLE',
    created_at          TIMESTAMPTZ     NOT NULL,
    updated_at          TIMESTAMPTZ
);
