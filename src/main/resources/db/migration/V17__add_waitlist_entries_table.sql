-- Phase 9 — Waitlist (join/position tracking only; see WaitlistEntry.java's
-- javadoc for why the notify-on-inventory-freed trigger is out of scope).
--
-- No FK constraints anywhere, matching every other table in this schema
-- (see V6 through V16).

-- Immutable, append-only tier per the ERD's audit-column policy - a single
-- creation timestamp (created_at) and nothing else, same tier as
-- CartItem/TicketTransfer. notified_at/offer_expires_at are set-once-later
-- fields (Phase 11's job), not a general-purpose updated_at.
CREATE TABLE waitlist_entries (
    id                  UUID            PRIMARY KEY,
    event_id            UUID            NOT NULL,
    ticket_type_id      UUID,
    user_id             UUID            NOT NULL,
    position            INT             NOT NULL,
    notified_at         TIMESTAMPTZ,
    offer_expires_at    TIMESTAMPTZ,
    created_at          TIMESTAMPTZ     NOT NULL
);

-- BR-WAIT-001: a user may only join once per event+ticket-type combination -
-- DB-level backstop (defense in depth, same idiom as V14's resale-listing
-- partial unique index), split into two partial indexes since a plain
-- unique index would treat every NULL ticket_type_id as distinct and never
-- actually catch the event-general (ticket_type_id omitted) duplicate case.
CREATE UNIQUE INDEX uq_waitlist_entries_specific_ticket_type
    ON waitlist_entries (event_id, ticket_type_id, user_id) WHERE ticket_type_id IS NOT NULL;
CREATE UNIQUE INDEX uq_waitlist_entries_event_general
    ON waitlist_entries (event_id, user_id) WHERE ticket_type_id IS NULL;

-- BR-WAIT-002: position must be FIFO by join time with no duplicates/gaps
-- ambiguity. WaitlistServiceImpl computes position as "current count + 1"
-- with no row to lock beforehand (unlike an existing Order/Ticket row) -
-- this is the actual race guard: two concurrent joins computing the same
-- position both attempt to insert it, and the loser's insert fails here,
-- triggering an app-level retry (same idiom as V14's resale-listing
-- active-ticket backstop).
CREATE UNIQUE INDEX uq_waitlist_entries_specific_position
    ON waitlist_entries (event_id, ticket_type_id, position) WHERE ticket_type_id IS NOT NULL;
CREATE UNIQUE INDEX uq_waitlist_entries_general_position
    ON waitlist_entries (event_id, position) WHERE ticket_type_id IS NULL;

CREATE INDEX idx_waitlist_entries_user_id ON waitlist_entries (user_id);
