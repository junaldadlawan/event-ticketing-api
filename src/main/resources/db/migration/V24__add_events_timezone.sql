-- events.timezone (Event.timezone, @Column(name = "timezone", nullable = false))
-- was never added by any prior Flyway migration - like image/ticket_prefix
-- before V8, it only ever existed in dev via ddl-auto (see V8's comment,
-- which lists timezone as needing to be formalized but never actually adds
-- it). Backfill existing rows to a safe default before constraining NOT
-- NULL, same pattern V3 used for events.category.
ALTER TABLE events ADD COLUMN IF NOT EXISTS timezone VARCHAR(64);

UPDATE events SET timezone = 'UTC' WHERE timezone IS NULL;

ALTER TABLE events ALTER COLUMN timezone SET NOT NULL;
