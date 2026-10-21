-- BR-NOTIFY-002: event lifecycle notifications carry their own text and the event they are about, so the bell can show
-- and open them. sent_for tells EVENT_CHANGE notifications apart (one per save: the save's time); the scheduled types
-- (reminder, gates open, starting, review request) go out once per user per event, whatever sent_for is.
ALTER TABLE notifications ADD COLUMN message VARCHAR(500);
ALTER TABLE notifications ADD COLUMN event_id UUID;
ALTER TABLE notifications ADD COLUMN sent_for TIMESTAMPTZ;

CREATE UNIQUE INDEX uq_notifications_event_lifecycle ON notifications (user_id, event_id, type)
    WHERE event_id IS NOT NULL AND type <> 'EVENT_CHANGE';
CREATE UNIQUE INDEX uq_notifications_event_change ON notifications (user_id, event_id, type, sent_for)
    WHERE event_id IS NOT NULL AND type = 'EVENT_CHANGE';
CREATE INDEX idx_notifications_event_type ON notifications (event_id, type) WHERE event_id IS NOT NULL;

-- Scheduled notifications whose time is before this moment are never sent: set when the event is published or moved,
-- so a reminder whose time had already passed is not sent late. Existing events start from now (no catch-up burst).
ALTER TABLE events ADD COLUMN notifications_from TIMESTAMPTZ;
UPDATE events SET notifications_from = now();

-- A user's opt-outs for the notification types they may turn off. No row = on.
CREATE TABLE notification_preferences (
    user_id     UUID          NOT NULL,
    type        VARCHAR(30)   NOT NULL,
    enabled     BOOLEAN       NOT NULL,
    updated_at  TIMESTAMPTZ   NOT NULL,
    PRIMARY KEY (user_id, type)
);
