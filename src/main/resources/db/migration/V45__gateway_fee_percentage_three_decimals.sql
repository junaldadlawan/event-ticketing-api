-- The payment gateway's percentage has three decimals (PayMongo's card rate is 3.125%), so the setting and the order snapshot keep 3
-- decimals instead of 2. V44 is already applied, so this is a separate migration. Existing values keep their number (4.02 becomes 4.020).
ALTER TABLE payment_gateway_fee_settings ALTER COLUMN percentage TYPE NUMERIC(6,3);
ALTER TABLE orders ALTER COLUMN gateway_fee_percentage TYPE NUMERIC(6,3);
