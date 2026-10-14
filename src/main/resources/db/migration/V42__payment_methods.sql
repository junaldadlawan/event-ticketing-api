-- Cards a user has saved to pay with. Only what the payment provider reports about a card is kept (brand, last 4, expiry,
-- holder name) plus the provider's payment-method id; a card number or security code is never stored.
-- stripe_payment_method_id is NULL for cards saved while the mock gateway is active.
CREATE TABLE payment_methods (
    id                        UUID          PRIMARY KEY,
    user_id                   UUID          NOT NULL,
    stripe_payment_method_id  VARCHAR(100),
    holder_name               VARCHAR(100)  NOT NULL,
    brand                     VARCHAR(30)   NOT NULL,
    last4                     CHAR(4)       NOT NULL,
    exp_month                 SMALLINT      NOT NULL CHECK (exp_month BETWEEN 1 AND 12),
    exp_year                  SMALLINT      NOT NULL,
    is_primary                BOOLEAN       NOT NULL DEFAULT FALSE,
    created_by                VARCHAR(255)  NOT NULL,
    created_at                TIMESTAMPTZ   NOT NULL,
    updated_by                VARCHAR(255),
    updated_at                TIMESTAMPTZ,
    deleted_at                TIMESTAMPTZ
);

CREATE INDEX idx_payment_methods_user ON payment_methods (user_id) WHERE deleted_at IS NULL;

-- a user can never have two primary cards
CREATE UNIQUE INDEX uq_payment_methods_one_primary ON payment_methods (user_id) WHERE is_primary AND deleted_at IS NULL;

-- the same provider card is saved once per user
CREATE UNIQUE INDEX uq_payment_methods_user_stripe_pm ON payment_methods (user_id, stripe_payment_method_id)
    WHERE stripe_payment_method_id IS NOT NULL AND deleted_at IS NULL;
