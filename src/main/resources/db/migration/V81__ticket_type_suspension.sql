-- an admin's suspension of a ticket type, kept apart from the organizer's sales_paused; suspended while suspended_at is set
ALTER TABLE ticket_types ADD COLUMN suspended_at      TIMESTAMPTZ;
ALTER TABLE ticket_types ADD COLUMN suspended_by      UUID;
ALTER TABLE ticket_types ADD COLUMN suspension_reason VARCHAR(1000);
