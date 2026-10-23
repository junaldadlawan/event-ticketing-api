-- An admin can ask the applicant to revise a pending application: it stays PENDING, with this flag, the message and the date, until the applicant edits it.
ALTER TABLE organizations ADD COLUMN revision_requested BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE organizations ADD COLUMN revision_message VARCHAR(500);
ALTER TABLE organizations ADD COLUMN revision_requested_at TIMESTAMPTZ;
