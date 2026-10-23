-- BR-TICKET-GATE-001: the gate-open time belongs to the event, not to each ticket type (display only).
ALTER TABLE events ADD COLUMN gate_opens_minutes_before INTEGER CHECK (gate_opens_minutes_before BETWEEN 0 AND 1440);

-- Backfill: an event whose live ticket types all have the same gate time gets it; mixed, partly unset or none = null.
UPDATE events e
SET gate_opens_minutes_before = t.minutes
FROM (SELECT event_id, MIN(gate_opens_minutes_before) AS minutes
      FROM ticket_types
      WHERE deleted_at IS NULL
      GROUP BY event_id
      HAVING COUNT(*) = COUNT(gate_opens_minutes_before)
         AND MIN(gate_opens_minutes_before) = MAX(gate_opens_minutes_before)) t
WHERE e.id = t.event_id;

ALTER TABLE ticket_types DROP COLUMN gate_opens_minutes_before;
