-- Links an app user to their Stripe Customer. Created lazily the first time the user needs one (adding a card, checking out
-- with a saved card), so most rows stay NULL. One Stripe Customer belongs to at most one user.
ALTER TABLE users ADD COLUMN IF NOT EXISTS stripe_customer_id VARCHAR(64);

CREATE UNIQUE INDEX IF NOT EXISTS uq_users_stripe_customer_id ON users (stripe_customer_id);
