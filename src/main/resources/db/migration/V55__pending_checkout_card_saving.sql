-- What became of the buyer's "save this card" request: REQUESTED (the intent vaults the card), UNAVAILABLE (PayMongo refused vaulting on this account, the
-- payment went ahead without saving) or NOT_REQUESTED. Kept so a repeated checkout call answers the same.
ALTER TABLE pending_checkouts ADD COLUMN IF NOT EXISTS card_saving VARCHAR(16) NOT NULL DEFAULT 'NOT_REQUESTED';
UPDATE pending_checkouts SET card_saving = 'REQUESTED' WHERE save_card;
