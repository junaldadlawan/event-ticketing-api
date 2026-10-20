-- BR-ORG-008: per-member permissions for the ORGANIZER role. No FK constraints, same as organization_member_roles (V6).
CREATE TABLE organization_member_permissions (
    user_id         UUID         NOT NULL,
    organization_id UUID         NOT NULL,
    permission      VARCHAR(30)  NOT NULL,
    PRIMARY KEY (user_id, organization_id, permission)
);

-- Existing organizers keep doing everything they could: every permission except TEAM (member management stays the owner's).
INSERT INTO organization_member_permissions (user_id, organization_id, permission)
SELECT r.user_id, r.organization_id, p.permission
FROM organization_member_roles r
CROSS JOIN (VALUES ('EVENTS'), ('PROMO_CODES'), ('CHECK_IN'), ('ORDERS_REFUNDS'), ('FINANCE'), ('ANALYTICS')) AS p(permission)
WHERE r.role = 'ORGANIZER';
