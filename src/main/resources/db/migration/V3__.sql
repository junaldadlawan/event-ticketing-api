CREATE SEQUENCE IF NOT EXISTS revinfo_seq START WITH 1 INCREMENT BY 50;

CREATE TABLE revchanges
(
    rev        BIGINT NOT NULL,
    entityname VARCHAR(255)
);

CREATE TABLE revinfo
(
    rev      BIGINT NOT NULL,
    revtstmp BIGINT,
    CONSTRAINT pk_revinfo PRIMARY KEY (rev)
);

ALTER TABLE users
    ADD delete_at TIMESTAMP WITHOUT TIME ZONE;

ALTER TABLE revchanges
    ADD CONSTRAINT fk_revchanges_on_default_tracking_modified_entities_changelog FOREIGN KEY (rev) REFERENCES revinfo (rev);

ALTER TABLE events
DROP
COLUMN deleted_at;

UPDATE events
SET category = ''
WHERE category IS NULL;
ALTER TABLE events
    ALTER COLUMN category SET NOT NULL;

ALTER TABLE users
DROP
COLUMN created_by;

ALTER TABLE users
    ADD created_by VARCHAR(255) NOT NULL;

UPDATE events
SET description = ''
WHERE description IS NULL;
ALTER TABLE events
    ALTER COLUMN description SET NOT NULL;

ALTER TABLE events
    ALTER COLUMN organizer_id DROP NOT NULL;

ALTER TABLE users
ALTER
COLUMN updated_by TYPE VARCHAR(255) USING (updated_by::VARCHAR(255));