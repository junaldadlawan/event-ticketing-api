-- A second gateway fee rate for cards issued abroad (PayMongo charges those more). NULL foreign_percentage means "the same as the domestic rate",
-- so existing settings and every quote stay exactly as they were. The percentage and flat amount already there are the domestic rate.
ALTER TABLE payment_gateway_fee_settings ADD COLUMN IF NOT EXISTS foreign_percentage NUMERIC(6,3)
    CHECK (foreign_percentage IS NULL OR (foreign_percentage >= 0 AND foreign_percentage <= 100));
ALTER TABLE payment_gateway_fee_settings ADD COLUMN IF NOT EXISTS foreign_flat_amount BIGINT
    CHECK (foreign_flat_amount IS NULL OR foreign_flat_amount >= 0);

-- The customer pays the domestic rate in the cart (the card is not known yet). When the payment arrives the order records whether the card was
-- DOMESTIC or FOREIGN and the fee that really applied to it (gateway_fee_applied_amount, the foreign fee for a foreign card). Null on orders
-- from before this existed and when the gateway does not say where the card is from; the payout then uses gateway_fee_amount as before.
ALTER TABLE orders ADD COLUMN IF NOT EXISTS gateway_fee_card_origin VARCHAR(10);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS gateway_fee_applied_amount BIGINT;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS gateway_fee_foreign_percentage NUMERIC(6,3);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS gateway_fee_foreign_flat_amount BIGINT;
