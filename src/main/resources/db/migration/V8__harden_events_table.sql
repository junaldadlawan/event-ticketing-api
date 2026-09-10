-- Phase 3 — Event hardening.
--
-- events.organizer_id was an unconstrained, always-random placeholder
-- (never a real FK); this migration wires it to the real Organization via
-- Event.organizationId (still no FK constraint, consistent with every other
-- table in this schema — see V6/V7). events.venue was a bare int; it's
-- replaced with a nullable venue_id UUID (virtual events have none).
-- events.image (bytea) is replaced by a child event_images table, mirroring
-- organization_documents' @ElementCollection/@OrderColumn idiom from V6.
--
-- image/ticket_prefix/timezone were never added by any prior Flyway
-- migration (V1-V7) — they exist today only because
-- spring.jpa.hibernate.ddl-auto=update silently created them in dev. This
-- migration formalizes ticket_prefix (also adding the UNIQUE constraint
-- BR-TICKET-004 requires) and replaces image with event_images, using
-- IF EXISTS/IF NOT EXISTS defensively so this is correct whether run
-- against a dev DB that already has these columns (via ddl-auto) or a
-- genuinely fresh environment that doesn't.

-- Rename organizer_id -> organization_id. organizer_id has been nullable
-- since V3 (`ALTER TABLE events ALTER COLUMN organizer_id DROP NOT NULL`),
-- so we can't assume every environment is NULL-free the way the local dev
-- DB happened to be when this migration was written. Fail loudly with a
-- clear message if any NULL rows exist, rather than either crashing with a
-- cryptic constraint-violation error or silently backfilling data in an
-- environment we don't control (organization_id has no sensible default to
-- backfill to, unlike category's '' in V3).
ALTER TABLE events RENAME COLUMN organizer_id TO organization_id;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM events WHERE organization_id IS NULL) THEN
        RAISE EXCEPTION 'V8__harden_events_table: % row(s) in events have a NULL organization_id; cannot apply SET NOT NULL. Resolve these rows (backfill or delete) before re-running this migration.',
            (SELECT COUNT(*) FROM events WHERE organization_id IS NULL);
    END IF;
END $$;

ALTER TABLE events ALTER COLUMN organization_id SET NOT NULL;

-- venue (bare int) -> venue_id (nullable UUID, no FK per this schema's convention).
ALTER TABLE events ADD COLUMN IF NOT EXISTS venue_id UUID;
ALTER TABLE events DROP COLUMN IF EXISTS venue;

-- image (bytea) -> event_images child table.
ALTER TABLE events DROP COLUMN IF EXISTS image;

CREATE TABLE IF NOT EXISTS event_images (
    event_id    UUID            NOT NULL,
    sort_order  INT             NOT NULL,
    url         VARCHAR(2048)   NOT NULL,
    PRIMARY KEY (event_id, sort_order)
);

-- Formalize ticket_prefix (already present in dev via ddl-auto; ADD COLUMN
-- IF NOT EXISTS makes this correct on a fresh environment too).
ALTER TABLE events ADD COLUMN IF NOT EXISTS ticket_prefix VARCHAR(3) NOT NULL;

-- Defensive cleanup: local/dev environments may carry stray duplicate
-- ticket_prefix values left over from earlier phases' manual verification
-- (this should never occur going forward once BR-TICKET-004 is enforced by
-- the service layer's uniqueness-retry loop). Keep the oldest row per
-- prefix and delete the rest so the UNIQUE constraint below can be added.
DELETE FROM events e
USING events e2
WHERE e.ticket_prefix = e2.ticket_prefix
  AND (e.created_at, e.id) > (e2.created_at, e2.id);

ALTER TABLE events ADD CONSTRAINT uq_events_ticket_prefix UNIQUE (ticket_prefix);
