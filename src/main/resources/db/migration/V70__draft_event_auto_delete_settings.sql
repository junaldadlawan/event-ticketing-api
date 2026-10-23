-- Drafts that were never published in time are deleted automatically: an event still in DRAFT whose start is less than this many minutes away (or
-- already past) is soft deleted by a scheduled job. Single row, seeded with 60 (one hour); 0 switches the rule off.
CREATE TABLE draft_event_settings (
    id                               SMALLINT      PRIMARY KEY CHECK (id = 1),
    auto_delete_minutes_before_start INTEGER       NOT NULL DEFAULT 60 CHECK (auto_delete_minutes_before_start BETWEEN 0 AND 10080),
    created_by                       VARCHAR(255)  NOT NULL,
    created_at                       TIMESTAMPTZ   NOT NULL,
    updated_by                       VARCHAR(255),
    updated_at                       TIMESTAMPTZ,
    deleted_at                       TIMESTAMPTZ
);

INSERT INTO draft_event_settings (id, created_by, created_at) VALUES (1, 'system', now());
