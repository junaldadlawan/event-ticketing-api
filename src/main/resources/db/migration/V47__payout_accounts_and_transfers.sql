-- Paying organizers through PayMongo: PayMongo sends money from the platform's wallet to a bank account or e-wallet (InstaPay or PESONet),
-- never to a card, so an organization now has a payout account next to its payout cards. One live account per organization. The full account
-- number is kept only AES-256-GCM encrypted (account_number_encrypted); the last 4 are kept in the clear for display.
CREATE TABLE payout_accounts (
    id                        UUID            PRIMARY KEY,
    organization_id           UUID            NOT NULL,
    bank_name                 VARCHAR(100)    NOT NULL,
    bank_code                 VARCHAR(20)     NOT NULL,
    account_name              VARCHAR(150)    NOT NULL,
    account_number_encrypted  VARCHAR(500)    NOT NULL,
    account_number_last4      VARCHAR(4)      NOT NULL,
    created_by                VARCHAR(255)    NOT NULL,
    created_at                TIMESTAMPTZ     NOT NULL,
    updated_by                VARCHAR(255),
    updated_at                TIMESTAMPTZ,
    deleted_at                TIMESTAMPTZ
);

CREATE UNIQUE INDEX uq_payout_accounts_org ON payout_accounts (organization_id) WHERE deleted_at IS NULL;

-- A payout remembers the account it was sent to and how the transfer went: transfer_id is PayMongo's id (tr_...), transfer_attempts counts
-- the sends (each retry uses a new reference number), failure_reason is why the last attempt failed. status gains PROCESSING (the
-- transfer is with PayMongo and not settled yet); the column is plain text, so nothing to alter.
ALTER TABLE payouts ADD COLUMN IF NOT EXISTS payout_account_id UUID;
ALTER TABLE payouts ADD COLUMN IF NOT EXISTS transfer_id VARCHAR(64);
ALTER TABLE payouts ADD COLUMN IF NOT EXISTS transfer_attempts INT NOT NULL DEFAULT 0;
ALTER TABLE payouts ADD COLUMN IF NOT EXISTS failure_reason VARCHAR(255);

CREATE UNIQUE INDEX IF NOT EXISTS uq_payouts_transfer_id ON payouts (transfer_id) WHERE transfer_id IS NOT NULL;
