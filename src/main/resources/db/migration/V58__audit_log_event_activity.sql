-- Per-event activity history (BR-EVENT-006): every audit entry about an event, its ticket types, promo codes,
-- templates, posts and policies carries the event it belongs to, so the event management screen can list them.
ALTER TABLE audit_log_entries ADD COLUMN event_id UUID;

-- An action the system takes on its own (an event completing when it ends) has no actor.
ALTER TABLE audit_log_entries ALTER COLUMN actor_id DROP NOT NULL;

CREATE INDEX idx_audit_log_entries_event_created ON audit_log_entries (event_id, created_at DESC);
