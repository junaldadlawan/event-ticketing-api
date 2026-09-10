-- Phase 5b — Order/Payment entities and POST /carts/{cartId}/checkout.
--
-- No FK constraints anywhere, matching every other table in this schema
-- (see V6/V7/V8/V9/V10).

-- Transactional/status-bearing tier per the ERD's audit-column policy:
-- created_by/created_at/updated_at only, no updated_by/deleted_at (see
-- Order.java javadoc).
CREATE TABLE orders (
    id                  UUID            PRIMARY KEY,
    buyer_id            UUID            NOT NULL,
    payee_type          VARCHAR(20)     NOT NULL,
    payee_id            UUID            NOT NULL,
    status              VARCHAR(20)     NOT NULL,
    promo_code          VARCHAR(50),
    -- Nullable: future non-cart order paths (e.g. Phase 7 resale) won't have
    -- a cart to reference. Partial unique index below (not a plain UNIQUE
    -- constraint) so multiple NULLs remain allowed, while still giving a
    -- hard DB-level backstop against two Orders ever being created for the
    -- same cart (code-reviewer CRITICAL 1 - defense in depth alongside
    -- CartRepository.findByIdForUpdate's row lock in CheckoutServiceImpl).
    cart_id             UUID,
    total_amount        BIGINT          NOT NULL,
    total_currency      VARCHAR(3)      NOT NULL,
    created_by          VARCHAR(255)    NOT NULL,
    created_at          TIMESTAMPTZ     NOT NULL,
    updated_at          TIMESTAMPTZ
);

CREATE UNIQUE INDEX uq_orders_cart_id ON orders (cart_id) WHERE cart_id IS NOT NULL;

-- Support the promo-code usage-limit re-check CheckoutServiceImpl.doCheckout
-- now runs immediately before charging (BR-PROMO-002/006), same queries
-- CartServiceImpl.applyPromoCode already used these columns for.
CREATE INDEX idx_orders_promo_code_status ON orders (promo_code, status);
CREATE INDEX idx_orders_promo_code_buyer_status ON orders (promo_code, buyer_id, status);

-- Matches openapi.yaml's Payment schema exactly: no created_by/deleted_at.
CREATE TABLE payments (
    id                  UUID            PRIMARY KEY,
    order_id            UUID            NOT NULL,
    gateway_ref         VARCHAR(255)    NOT NULL,
    amount_amount       BIGINT          NOT NULL,
    amount_currency     VARCHAR(3)      NOT NULL,
    status              VARCHAR(20)     NOT NULL,
    created_at          TIMESTAMPTZ     NOT NULL,
    updated_at          TIMESTAMPTZ
);

-- Purely internal bookkeeping for the required Idempotency-Key header
-- (BR-NFR-008) - never exposed via any API response. id is the
-- client-supplied header value itself, not server-generated.
CREATE TABLE checkout_idempotency_keys (
    id                  UUID            PRIMARY KEY,
    buyer_id            UUID            NOT NULL,
    cart_id             UUID            NOT NULL,
    order_id            UUID,
    created_at          TIMESTAMPTZ     NOT NULL
);

CREATE INDEX idx_checkout_idempotency_keys_buyer_id ON checkout_idempotency_keys (buyer_id);
CREATE INDEX idx_checkout_idempotency_keys_cart_id ON checkout_idempotency_keys (cart_id);
