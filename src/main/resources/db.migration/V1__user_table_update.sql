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

ALTER TABLE events
    ADD created_by VARCHAR(255);

ALTER TABLE events
    ADD image BYTEA;

ALTER TABLE events
    ADD ticket_prefix VARCHAR(3);

ALTER TABLE events
    ADD timezone VARCHAR(255);

ALTER TABLE events
    ALTER COLUMN created_by SET NOT NULL;

ALTER TABLE events
    ALTER COLUMN ticket_prefix SET NOT NULL;

ALTER TABLE events
    ALTER COLUMN timezone SET NOT NULL;

ALTER TABLE revchanges
    ADD CONSTRAINT fk_revchanges_on_default_tracking_modified_entities_changelog FOREIGN KEY (rev) REFERENCES revinfo (rev);

ALTER TABLE events
DROP
COLUMN create_by;

ALTER TABLE events
ALTER
COLUMN category TYPE VARCHAR(20) USING (category::VARCHAR(20));

ALTER TABLE events
    ALTER COLUMN category SET NOT NULL;

ALTER TABLE events
ALTER
COLUMN description TYPE VARCHAR(2000) USING (description::VARCHAR(2000));

ALTER TABLE events
    ALTER COLUMN description SET NOT NULL;

ALTER TABLE events
    ALTER COLUMN organizer_id DROP NOT NULL;

ALTER TABLE events
ALTER
COLUMN title TYPE VARCHAR(100) USING (title::VARCHAR(100));

ALTER TABLE events
    ALTER COLUMN updated_at DROP NOT NULL;

ALTER TABLE events
ALTER
COLUMN updated_by TYPE VARCHAR(255) USING (updated_by::VARCHAR(255));

ALTER TABLE events
    ALTER COLUMN updated_by DROP NOT NULL;