-- Phase 13 (BR-NFR-005). Append-only audit trail - no columns are ever
-- updated after insert (same "no-FK-constraints, no updated_at" convention
-- as V21's moderation_actions table, see V6 migration header).
CREATE TABLE audit_log_entries (
    id              UUID            PRIMARY KEY,
    actor_id        UUID            NOT NULL,
    action          VARCHAR(100)    NOT NULL,
    target_type     VARCHAR(50),
    target_id       UUID,
    created_at      TIMESTAMPTZ     NOT NULL
);

CREATE INDEX idx_audit_log_entries_actor_id ON audit_log_entries (actor_id);
CREATE INDEX idx_audit_log_entries_created_at ON audit_log_entries (created_at DESC);
