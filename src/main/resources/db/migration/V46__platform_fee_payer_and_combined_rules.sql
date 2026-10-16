-- The platform fee gets a payer like the payment gateway fee: CUSTOMER (added on top of the price, what it always was), ORGANIZATION
-- (deducted from the organizer's payout, the buyer pays nothing extra) or PLATFORM (waived). Existing rules and orders were all CUSTOMER.
-- A rule can now hold a percentage and a fixed amount together (fee_type COMBINED); both columns were already nullable.
ALTER TABLE platform_fee_rules ADD COLUMN IF NOT EXISTS payer VARCHAR(20) NOT NULL DEFAULT 'CUSTOMER';
ALTER TABLE orders ADD COLUMN IF NOT EXISTS platform_fee_payer VARCHAR(20);
