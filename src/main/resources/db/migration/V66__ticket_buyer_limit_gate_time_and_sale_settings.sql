-- BR-TICKET-LIMIT-001: how many tickets of a type one buyer may buy in total (null = unlimited).
-- BR-TICKET-GATE-001: gates open this many minutes before the event starts (null = not set; display only).
ALTER TABLE ticket_types ADD COLUMN max_per_user INTEGER CHECK (max_per_user >= 1);
ALTER TABLE ticket_types ADD COLUMN gate_opens_minutes_before INTEGER CHECK (gate_opens_minutes_before BETWEEN 0 AND 1440);

-- BR-ADMIN-SETTINGS-002: the two ticket-sale timing rules, set by admins. Single row (id = 1), seeded with the
-- values that were hard-coded until now (1 hour each).
CREATE TABLE ticket_sale_settings (
    id                                SMALLINT      PRIMARY KEY CHECK (id = 1),
    sales_close_minutes_before_event  INTEGER       NOT NULL DEFAULT 60 CHECK (sales_close_minutes_before_event BETWEEN 0 AND 10080),
    min_sale_duration_minutes         INTEGER       NOT NULL DEFAULT 60 CHECK (min_sale_duration_minutes BETWEEN 1 AND 10080),
    created_by                        VARCHAR(255)  NOT NULL,
    created_at                        TIMESTAMPTZ   NOT NULL,
    updated_by                        VARCHAR(255),
    updated_at                        TIMESTAMPTZ,
    deleted_at                        TIMESTAMPTZ
);

INSERT INTO ticket_sale_settings (id, created_by, created_at) VALUES (1, 'system', now());
