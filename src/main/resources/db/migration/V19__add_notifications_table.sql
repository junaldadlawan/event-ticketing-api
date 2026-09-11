-- Phase 11 — Notifications.
--
-- No FK constraints anywhere, matching every other table in this schema
-- (see V6 through V18).

-- System-triggered append-only tier per the ERD: only created_at (the
-- trigger timestamp), no updated_at/updated_by/deleted_at - status
-- transitions (pending -> sent/failed) happen in place on the same row,
-- same tier as CheckInRecord/TicketTransfer, not a general revision-
-- tracking entity.
CREATE TABLE notifications (
    id                  UUID            PRIMARY KEY,
    user_id             UUID            NOT NULL,
    type                VARCHAR(30)     NOT NULL,
    channel             VARCHAR(10)     NOT NULL,
    related_object_type VARCHAR(50),
    related_object_id   UUID,
    status              VARCHAR(10)     NOT NULL,
    sent_at             TIMESTAMPTZ,
    created_at          TIMESTAMPTZ     NOT NULL
);

CREATE INDEX idx_notifications_user_id_created_at ON notifications (user_id, created_at DESC);
