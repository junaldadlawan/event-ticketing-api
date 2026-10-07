-- Where the scannable code sits on a ticket template, set by the organizer.
-- code_type is QR or BARCODE; code_x/code_y are the unrotated box's top-left
-- corner as % of the ticket's width/height; code_width is its width as % of the
-- ticket's width (the height follows from the type: QR square, barcode 3:1);
-- code_rotation is degrees clockwise about the box centre (0-359, any type).
-- All NULL = the renderer's default placement.
ALTER TABLE ticket_templates ADD COLUMN IF NOT EXISTS code_type     VARCHAR(10);
ALTER TABLE ticket_templates ADD COLUMN IF NOT EXISTS code_x        DOUBLE PRECISION;
ALTER TABLE ticket_templates ADD COLUMN IF NOT EXISTS code_y        DOUBLE PRECISION;
ALTER TABLE ticket_templates ADD COLUMN IF NOT EXISTS code_width    DOUBLE PRECISION;
ALTER TABLE ticket_templates ADD COLUMN IF NOT EXISTS code_rotation INTEGER;
