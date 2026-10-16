-- The payment gateway's processing fee (PayMongo, Stripe...). Management sets, in ONE place, how much to apply and WHO pays it:
-- the CUSTOMER (added on top of the price and shown in the cart), the ORGANIZATION (deducted from its payout) or the PLATFORM (absorbed).
-- Single row (id = 1). Default: 0% and PLATFORM, so nothing changes until an admin sets it.
CREATE TABLE payment_gateway_fee_settings (
    id             SMALLINT       PRIMARY KEY CHECK (id = 1),
    percentage     NUMERIC(5,2)   NOT NULL DEFAULT 0 CHECK (percentage >= 0 AND percentage <= 100),
    flat_amount    BIGINT         NOT NULL DEFAULT 0 CHECK (flat_amount >= 0),
    flat_currency  VARCHAR(3)     NOT NULL DEFAULT 'PHP',
    payer          VARCHAR(20)    NOT NULL DEFAULT 'PLATFORM',
    created_by     VARCHAR(255)   NOT NULL,
    created_at     TIMESTAMPTZ    NOT NULL,
    updated_by     VARCHAR(255),
    updated_at     TIMESTAMPTZ,
    deleted_at     TIMESTAMPTZ
);

INSERT INTO payment_gateway_fee_settings (id, created_by, created_at) VALUES (1, 'system', now());

-- What an order was charged, snapshotted like the platform fee so changing the setting never rewrites old orders.
-- gateway_fee_amount is the fee the gateway cost on this order; gateway_fee_payer says who bears it. It is part of the order total
-- only when the payer is CUSTOMER.
ALTER TABLE orders ADD COLUMN IF NOT EXISTS gateway_fee_amount BIGINT NOT NULL DEFAULT 0;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS gateway_fee_payer VARCHAR(20);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS gateway_fee_percentage NUMERIC(5,2);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS gateway_fee_flat_amount BIGINT;
