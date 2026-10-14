-- Payout cards replace the bank-account payout destination added in V39. V39 was applied on dev, so it is left as it is
-- and undone here; both of its tables were checked empty (no accounts, no payout with a snapshot) before this was written.
DROP TABLE IF EXISTS organization_payout_accounts;
ALTER TABLE payouts DROP COLUMN IF EXISTS payout_bank_name;
ALTER TABLE payouts DROP COLUMN IF EXISTS payout_account_holder;
ALTER TABLE payouts DROP COLUMN IF EXISTS payout_account_last4;

-- A card an organization is paid out to. Only what a payment gateway hands back is kept: a token, brand, last 4, expiry
-- and holder name. The full card number and the security code are never stored.
CREATE TABLE payout_cards (
    id               UUID          PRIMARY KEY,
    organization_id  UUID          NOT NULL,
    holder_name      VARCHAR(100)  NOT NULL,
    brand            VARCHAR(30)   NOT NULL,
    last4            VARCHAR(4)    NOT NULL,
    exp_month        SMALLINT      NOT NULL CHECK (exp_month BETWEEN 1 AND 12),
    exp_year         SMALLINT      NOT NULL,
    payment_token    VARCHAR(255)  NOT NULL,
    is_primary       BOOLEAN       NOT NULL DEFAULT FALSE,
    created_by       VARCHAR(255)  NOT NULL,
    created_at       TIMESTAMPTZ   NOT NULL,
    updated_by       VARCHAR(255),
    updated_at       TIMESTAMPTZ,
    deleted_at       TIMESTAMPTZ
);

CREATE INDEX idx_payout_cards_org ON payout_cards (organization_id) WHERE deleted_at IS NULL;

-- exactly one primary card per organization
CREATE UNIQUE INDEX uq_payout_cards_one_primary ON payout_cards (organization_id) WHERE is_primary AND deleted_at IS NULL;

-- the card a payout was made out to
ALTER TABLE payouts ADD COLUMN IF NOT EXISTS payout_card_id UUID;
