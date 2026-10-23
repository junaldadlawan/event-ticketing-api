-- Organization detail fields (all optional) and the change requests that edit an APPROVED organization: the live row changes only when an admin approves.
ALTER TABLE organizations ADD COLUMN contact_email VARCHAR(255);
ALTER TABLE organizations ADD COLUMN contact_phone VARCHAR(30);
ALTER TABLE organizations ADD COLUMN address VARCHAR(500);
ALTER TABLE organizations ADD COLUMN description VARCHAR(2000);

CREATE TABLE organization_change_requests (
    id               UUID          PRIMARY KEY,
    organization_id  UUID          NOT NULL,
    requested_by     UUID          NOT NULL,
    -- JSON object of the changed fields only: {"name": "New", "address": null}; null = clear the field
    changes          TEXT          NOT NULL,
    status           VARCHAR(20)   NOT NULL,
    decided_by       UUID,
    decided_at       TIMESTAMPTZ,
    rejection_reason VARCHAR(500),
    created_by       VARCHAR(255)  NOT NULL,
    created_at       TIMESTAMPTZ   NOT NULL,
    updated_by       VARCHAR(255),
    updated_at       TIMESTAMPTZ,
    deleted_at       TIMESTAMPTZ
);

CREATE INDEX idx_org_change_requests_org ON organization_change_requests (organization_id, created_at DESC);
-- one pending request per organization (a new request replaces it)
CREATE UNIQUE INDEX uq_org_change_requests_one_pending ON organization_change_requests (organization_id) WHERE status = 'PENDING' AND deleted_at IS NULL;
