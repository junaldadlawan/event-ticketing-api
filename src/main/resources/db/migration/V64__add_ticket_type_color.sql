-- Display colour per ticket type ("#RRGGBB", stored uppercase); null = none, the web app's default grey.
ALTER TABLE ticket_types ADD COLUMN color VARCHAR(7);
