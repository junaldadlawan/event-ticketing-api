-- Data fix: User.role is shrinking to {CUSTOMER, ADMIN}; ORGANIZER/CHECK_IN_STAFF
-- move to the new org-scoped OrganizationMember.roles. Lossy on purpose — local/dev
-- DB only, no production data to preserve.
UPDATE users SET role = 'CUSTOMER' WHERE role IN ('ORGANIZER', 'CHECK_IN_STAFF');

CREATE TABLE organizations (
    id                  UUID            PRIMARY KEY,
    name                VARCHAR(255)    NOT NULL,
    status              VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
    owner_id            UUID,
    rejection_reason    VARCHAR(500),
    created_by          VARCHAR(255)    NOT NULL,
    created_at          TIMESTAMPTZ     NOT NULL,
    updated_by          VARCHAR(255),
    updated_at          TIMESTAMPTZ,
    deleted_at          TIMESTAMPTZ
);

CREATE TABLE organization_documents (
    organization_id     UUID            NOT NULL,
    sort_order           INT             NOT NULL,
    type                 VARCHAR(100)    NOT NULL,
    url                  VARCHAR(500)    NOT NULL,
    PRIMARY KEY (organization_id, sort_order)
);

CREATE TABLE organization_members (
    user_id              UUID            NOT NULL,
    organization_id      UUID            NOT NULL,
    assigned_at           TIMESTAMPTZ     NOT NULL,
    PRIMARY KEY (user_id, organization_id)
);

CREATE TABLE organization_member_roles (
    user_id              UUID            NOT NULL,
    organization_id      UUID            NOT NULL,
    role                  VARCHAR(20)     NOT NULL,
    PRIMARY KEY (user_id, organization_id, role)
);
