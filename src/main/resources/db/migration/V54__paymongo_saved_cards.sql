-- Saved cards on PayMongo: a card is vaulted on a PayMongo customer by a payment made with setup_future_usage, and charged again with its payment
-- method id (pm_...). Never a card number or a security code. Cards saved before this have no payment method id and cannot be charged again.
ALTER TABLE users ADD COLUMN IF NOT EXISTS paymongo_customer_id VARCHAR(64);
CREATE UNIQUE INDEX IF NOT EXISTS uq_users_paymongo_customer_id ON users (paymongo_customer_id) WHERE paymongo_customer_id IS NOT NULL;

ALTER TABLE payment_methods ADD COLUMN IF NOT EXISTS paymongo_payment_method_id VARCHAR(100);
CREATE INDEX IF NOT EXISTS idx_payment_methods_paymongo_pm ON payment_methods (user_id, paymongo_payment_method_id);

-- a checkout can ask to save the card; a saved card is attached by the server, which may bring a 3-D Secure redirect
ALTER TABLE pending_checkouts ADD COLUMN IF NOT EXISTS save_card BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE pending_checkouts ADD COLUMN IF NOT EXISTS attached_at TIMESTAMPTZ;
ALTER TABLE pending_checkouts ADD COLUMN IF NOT EXISTS redirect_url TEXT;
