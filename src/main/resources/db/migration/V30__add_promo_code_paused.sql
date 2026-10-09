-- Organizers can pause / resume a promo code without deleting it: while paused it can't be applied to a cart
-- and a cart that already holds it can't check out. Orders that used it are untouched.
ALTER TABLE promo_codes ADD COLUMN IF NOT EXISTS paused BOOLEAN NOT NULL DEFAULT FALSE;

-- A promo code can now be soft-deleted (only when nothing used it) and a code can be renamed, so the
-- (event_id, code) uniqueness has to ignore deleted rows: the name of a deleted code is free to use again.
ALTER TABLE promo_codes DROP CONSTRAINT IF EXISTS uq_promo_codes_event_code;
CREATE UNIQUE INDEX IF NOT EXISTS uq_promo_codes_event_code_active ON promo_codes (event_id, code) WHERE deleted_at IS NULL;
