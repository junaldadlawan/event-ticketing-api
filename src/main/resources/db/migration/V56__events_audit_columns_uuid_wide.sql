-- events.created_by / updated_by were VARCHAR(20) in V1 and V3 widened only users' copies. The auditor name is the user's UUID (36 characters),
-- so creating an event failed with "value too long for type character varying(20)" on every database built by Flyway alone (a dev database
-- made by ddl-auto=update had 255 and hid it). Every other table's audit columns are already VARCHAR(255).
ALTER TABLE events ALTER COLUMN created_by TYPE VARCHAR(255);
ALTER TABLE events ALTER COLUMN updated_by TYPE VARCHAR(255);
