-- BR-EVENT-006: what changed, not just that something changed - e.g. {"changes":[{"field":"title","from":"A","to":"B"}]},
-- {"name":"VIP"} for a created/deleted item. Never secrets, card data or buyers' personal data. Older rows stay null.
ALTER TABLE audit_log_entries ADD COLUMN details JSONB;
