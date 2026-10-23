-- notification types grew past 30 characters (ORGANIZATION_REVISION_REQUESTED is 31)
ALTER TABLE notifications ALTER COLUMN type TYPE VARCHAR(50);
