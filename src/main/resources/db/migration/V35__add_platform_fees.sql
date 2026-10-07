-- Platform fee ("admin cut"): rules an admin sets, and the fee each order was charged.
--
-- platform_fee_rules: one rule per scope - the whole platform (scope_id NULL), one organization or one event.
-- The most specific rule wins (event, then organization, then platform). A rule is a percentage of the ticket
-- total (after promo discounts) or a flat amount per order; 0 is a valid rate and waives the fee for that scope.
CREATE TABLE platform_fee_rules (
    id             UUID            PRIMARY KEY,
    scope          VARCHAR(20)     NOT NULL,
    scope_id       UUID,
    fee_type       VARCHAR(20)     NOT NULL,
    percentage     NUMERIC(5,2),
    flat_amount    BIGINT,
    flat_currency  VARCHAR(3),
    created_by     VARCHAR(255)    NOT NULL,
    created_at     TIMESTAMPTZ     NOT NULL,
    updated_by     VARCHAR(255),
    updated_at     TIMESTAMPTZ,
    deleted_at     TIMESTAMPTZ
);

CREATE UNIQUE INDEX uq_platform_fee_rules_scope
    ON platform_fee_rules (scope, COALESCE(scope_id, '00000000-0000-0000-0000-000000000000'::uuid))
    WHERE deleted_at IS NULL;

-- orders: the fee is added on top of the ticket price and is part of total_amount (what the buyer paid). It is
-- stored with a snapshot of the rule that produced it, so later rule changes never rewrite old orders.
-- payout_id marks the payout an order was settled in (NULL = not paid out yet).
ALTER TABLE orders ADD COLUMN IF NOT EXISTS platform_fee_amount BIGINT NOT NULL DEFAULT 0;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS platform_fee_scope VARCHAR(20);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS platform_fee_type VARCHAR(20);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS platform_fee_percentage NUMERIC(5,2);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS platform_fee_flat_amount BIGINT;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS payout_id UUID;

CREATE INDEX IF NOT EXISTS idx_orders_payout_id ON orders (payout_id);
