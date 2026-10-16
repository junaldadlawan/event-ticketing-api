-- A checkout can pay only some of the cart's items. The selection (item ids, sorted, comma-separated) is kept on the idempotency key, so the same
-- key with another selection is refused, and on the pending checkout, so the payment webhook issues the order for exactly those items.
-- NULL = the whole cart (every checkout made before this migration).
ALTER TABLE checkout_idempotency_keys ADD COLUMN item_ids TEXT;
ALTER TABLE pending_checkouts ADD COLUMN item_ids TEXT;
