-- A payout can settle one event (the admin's "batch payout" lines are per organization and event). NULL = a period payout, which is what every
-- existing payout is: all the organization's due orders in a period, whatever event they belong to.
ALTER TABLE payouts ADD COLUMN IF NOT EXISTS event_id UUID;

CREATE INDEX IF NOT EXISTS idx_payouts_event_id ON payouts (event_id) WHERE event_id IS NOT NULL;
