-- Where an organization is paid: one payout account per organization (bank details). The full account number is
-- stored only AES-256-GCM encrypted (account_number_encrypted); the last 4 characters are kept in the clear for display
-- and for the snapshot a payout takes. Soft-deleted like the other managed resources.
CREATE TABLE organization_payout_accounts (
    id                        UUID            PRIMARY KEY,
    organization_id           UUID            NOT NULL,
    bank_name                 VARCHAR(100)    NOT NULL,
    account_holder            VARCHAR(150)    NOT NULL,
    account_number_encrypted  VARCHAR(500)    NOT NULL,
    account_number_last4      VARCHAR(4)      NOT NULL,
    branch_code               VARCHAR(20),
    created_by                VARCHAR(255)    NOT NULL,
    created_at                TIMESTAMPTZ     NOT NULL,
    updated_by                VARCHAR(255),
    updated_at                TIMESTAMPTZ,
    deleted_at                TIMESTAMPTZ
);

CREATE UNIQUE INDEX uq_organization_payout_accounts_org ON organization_payout_accounts (organization_id) WHERE deleted_at IS NULL;

-- A payout remembers which account it was made out to (never the full number), so changing or removing the account later
-- does not rewrite old payouts. NULL on payouts made before this existed.
ALTER TABLE payouts ADD COLUMN IF NOT EXISTS payout_bank_name VARCHAR(100);
ALTER TABLE payouts ADD COLUMN IF NOT EXISTS payout_account_holder VARCHAR(150);
ALTER TABLE payouts ADD COLUMN IF NOT EXISTS payout_account_last4 VARCHAR(4);
