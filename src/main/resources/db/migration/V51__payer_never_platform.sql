-- The platform can no longer be set as the payer of the platform fee or the payment gateway fee: only the customer (added on top of the price) or
-- the organization (deducted from its payout). Stored settings that said PLATFORM become CUSTOMER, which is what a rule without a payer means.
-- Orders already placed keep the payer they were placed with (their snapshot is not touched), so old payouts and reports do not change.
UPDATE payment_gateway_fee_settings SET payer = 'CUSTOMER' WHERE payer = 'PLATFORM';
UPDATE platform_fee_rules SET payer = 'CUSTOMER' WHERE payer = 'PLATFORM';
ALTER TABLE payment_gateway_fee_settings ALTER COLUMN payer SET DEFAULT 'CUSTOMER';
