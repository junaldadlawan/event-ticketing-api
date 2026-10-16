-- A checkout waiting for the buyer's card to be authorised on the payment provider's side (PayMongo): the provider's payment intent
-- exists, the buyer attaches a card in the browser (3-D Secure included) and the provider tells us the outcome by webhook.
-- BR-CART-002 still holds: an order exists only once the payment succeeded; until then there is only this row.
CREATE TABLE pending_checkouts (
    id                  UUID          PRIMARY KEY,
    cart_id             UUID          NOT NULL,
    buyer_id            UUID          NOT NULL,
    idempotency_key     UUID          NOT NULL,
    provider_intent_id  VARCHAR(100)  NOT NULL,
    client_key          VARCHAR(255)  NOT NULL,
    amount              BIGINT        NOT NULL,
    currency            VARCHAR(3)    NOT NULL,
    status              VARCHAR(20)   NOT NULL,
    order_id            UUID,
    failure_reason      VARCHAR(500),
    created_at          TIMESTAMPTZ   NOT NULL,
    updated_at          TIMESTAMPTZ
);

CREATE UNIQUE INDEX uq_pending_checkouts_intent ON pending_checkouts (provider_intent_id);
CREATE INDEX idx_pending_checkouts_key ON pending_checkouts (idempotency_key);
CREATE INDEX idx_pending_checkouts_buyer ON pending_checkouts (buyer_id);
