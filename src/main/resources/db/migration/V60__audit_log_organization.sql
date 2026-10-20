-- BR-ADMIN-ACTIVITY: per-tab activity logs. Each audit entry carries the organization it is about, so an
-- organization's log (GET /activity?organizationId=...) is an indexed query, not a scan.
ALTER TABLE audit_log_entries ADD COLUMN organization_id UUID;

-- Backfill from what older entries already point at.
UPDATE audit_log_entries a SET organization_id = e.organization_id
    FROM events e WHERE a.event_id = e.id;
UPDATE audit_log_entries a SET organization_id = a.target_id
    WHERE a.organization_id IS NULL AND a.target_type IN ('Organization', 'ORGANIZATION');
-- organization_member.assigned has always stored the organization as its target
UPDATE audit_log_entries a SET organization_id = a.target_id
    WHERE a.organization_id IS NULL AND a.action LIKE 'organization_member.%';
UPDATE audit_log_entries a SET organization_id = p.organization_id
    FROM payouts p WHERE a.organization_id IS NULL AND a.target_type = 'Payout' AND a.target_id = p.id;
UPDATE audit_log_entries a SET organization_id = c.organization_id
    FROM payout_cards c WHERE a.organization_id IS NULL AND a.target_type = 'PayoutCard' AND a.target_id = c.id;
UPDATE audit_log_entries a SET organization_id = pa.organization_id
    FROM payout_accounts pa WHERE a.organization_id IS NULL AND a.target_type = 'PayoutAccount' AND a.target_id = pa.id;
UPDATE audit_log_entries a SET organization_id = o.payee_id
    FROM refunds r JOIN orders o ON o.id = r.order_id
    WHERE a.organization_id IS NULL AND a.target_type = 'Refund' AND a.target_id = r.id AND o.payee_type = 'ORGANIZATION';

CREATE INDEX idx_audit_log_entries_org_created ON audit_log_entries (organization_id, created_at DESC);
