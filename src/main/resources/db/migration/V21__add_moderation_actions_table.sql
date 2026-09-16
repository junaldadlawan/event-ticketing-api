-- Phase 12 (BR-ADMIN-002). Append-only audit trail - no columns are ever
-- updated after insert (mirrors this schema's no-FK-constraints convention,
-- see V6 migration header).
CREATE TABLE moderation_actions (
    id                  UUID            PRIMARY KEY,
    target_type         VARCHAR(20)     NOT NULL,
    target_id           UUID            NOT NULL,
    action              VARCHAR(20)     NOT NULL,
    reason              VARCHAR(1000)   NOT NULL,
    previous_status     VARCHAR(20),
    performed_by        UUID            NOT NULL,
    created_at          TIMESTAMPTZ     NOT NULL
);

CREATE INDEX idx_moderation_actions_target ON moderation_actions (target_type, target_id);
