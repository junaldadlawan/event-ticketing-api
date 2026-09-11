-- Phase 7 (code-reviewer MEDIUM) — resale price-cap math was using the
-- ticket type's CURRENT price, not the price actually paid, which lets the
-- resale cap silently drift upward if an organizer raises tiered/early-bird
-- pricing after early buyers already checked out (the exact scalping-beyond-
-- face-value scenario BR-TRANSFER-004's price cap exists to prevent).
--
-- Nullable so pre-existing tickets (issued before this migration) fall back
-- to the ticket type's current price at resale time - same behavior as
-- before this fix, just no longer the ONLY behavior for newly-issued tickets.
ALTER TABLE tickets ADD COLUMN face_value_amount BIGINT;
ALTER TABLE tickets ADD COLUMN face_value_currency VARCHAR(3);
