-- Phase 12 (BR-ADMIN-002). Kept separate from V21 for a clean diff.
--
-- OrganizationStatus/EventStatus are already plain VARCHAR columns with no
-- DB CHECK constraint (this schema's established convention - see V6/V8),
-- so adding their new SUSPENDED enum value needs no migration at all.
--
-- User is the one target type with no existing status column to piggyback
-- on - accountStatus is a genuinely new column, defaulted to ACTIVE for
-- every existing row.
ALTER TABLE users ADD COLUMN account_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE';
