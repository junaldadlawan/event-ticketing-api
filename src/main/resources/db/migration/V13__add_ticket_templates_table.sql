-- Phase 6b — TicketTemplate entity.
--
-- No FK constraints anywhere, matching every other table in this schema
-- (see V6/V7/V8/V9/V10/V11/V12).

-- Managed-resource tier per the ERD's audit-column policy: full audit set,
-- same as ticket_types/seat_maps/promo_codes.
CREATE TABLE ticket_templates (
    id                      UUID            PRIMARY KEY,
    event_id                UUID            NOT NULL,
    ticket_type_id          UUID,
    format                  VARCHAR(20)     NOT NULL,
    logo_url                VARCHAR(2048),
    background_image_url    VARCHAR(2048),
    primary_color           VARCHAR(20),
    created_by              VARCHAR(255)    NOT NULL,
    created_at              TIMESTAMPTZ     NOT NULL,
    updated_by              VARCHAR(255),
    updated_at              TIMESTAMPTZ,
    deleted_at              TIMESTAMPTZ
);

CREATE INDEX idx_ticket_templates_event_id ON ticket_templates (event_id);
