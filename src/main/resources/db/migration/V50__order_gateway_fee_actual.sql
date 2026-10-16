-- What the payment gateway really kept on an order (its fee plus any foreign-card fee: the payment amount minus the net PayMongo paid out),
-- read from the gateway's own payment record. NULL = not known yet (an order from before this existed, or the gateway was not asked yet).
-- gateway_fee_amount / gateway_fee_applied_amount stay what they were: the fee the customer was quoted and the estimate used for payouts.
ALTER TABLE orders ADD COLUMN IF NOT EXISTS gateway_fee_actual_amount BIGINT;
