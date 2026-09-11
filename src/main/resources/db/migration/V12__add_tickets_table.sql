-- Phase 6a — Ticket entity, wired into checkout issuance.
--
-- No FK constraints anywhere, matching every other table in this schema
-- (see V6/V7/V8/V9/V10/V11).

-- Transactional/status-bearing tier per the ERD's audit-column policy:
-- created_at/updated_at only, no created_by/updated_by/deleted_at (see
-- Ticket.java javadoc). credential is a signed opaque token, never returned
-- via any API read.
CREATE TABLE tickets (
    id                  UUID            PRIMARY KEY,
    order_id            UUID            NOT NULL,
    event_id            UUID            NOT NULL,
    ticket_type_id      UUID            NOT NULL,
    seat_id             UUID,
    owner_id            UUID            NOT NULL,
    ticket_number       VARCHAR(20)     NOT NULL,
    credential          VARCHAR(500)    NOT NULL,
    status              VARCHAR(20)     NOT NULL,
    created_at          TIMESTAMPTZ     NOT NULL,
    updated_at          TIMESTAMPTZ
);

-- BR-TICKET-005: the human-readable ticket number's 6-character suffix only
-- needs to be unique within its own event (uniqueness comes from the event's
-- reserved 3-letter prefix) - same belt-and-suspenders approach as Event's
-- own ticket_prefix unique constraint (V1).
CREATE UNIQUE INDEX uq_tickets_event_id_ticket_number ON tickets (event_id, ticket_number);

-- Defense-in-depth against a credential collision (effectively impossible
-- given the embedded random UUID + HMAC signature).
CREATE UNIQUE INDEX uq_tickets_credential ON tickets (credential);

CREATE INDEX idx_tickets_order_id ON tickets (order_id);
CREATE INDEX idx_tickets_event_id ON tickets (event_id);
CREATE INDEX idx_tickets_owner_id ON tickets (owner_id);
