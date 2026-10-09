-- Pause / resume ticket sales for one ticket type without touching its sale window: while
-- sales_paused is TRUE the type can't be added to a cart (existing holds and tickets are unaffected).
ALTER TABLE ticket_types ADD COLUMN IF NOT EXISTS sales_paused BOOLEAN NOT NULL DEFAULT FALSE;
