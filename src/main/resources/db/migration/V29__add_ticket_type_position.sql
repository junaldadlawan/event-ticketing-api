-- Where a ticket type sits in its event's list (0 = first). The organizer arranges the list by dragging, and
-- the arrangement is saved here. Existing ticket types keep the order they were created in.
ALTER TABLE ticket_types ADD COLUMN IF NOT EXISTS position INTEGER NOT NULL DEFAULT 0;

UPDATE ticket_types t
SET position = ranked.position
FROM (
    SELECT id, row_number() OVER (PARTITION BY event_id ORDER BY created_at, id) - 1 AS position
    FROM ticket_types
    WHERE deleted_at IS NULL
) ranked
WHERE t.id = ranked.id;

CREATE INDEX IF NOT EXISTS idx_ticket_types_event_position ON ticket_types (event_id, position);
