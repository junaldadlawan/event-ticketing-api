CREATE TABLE venues (
    id                  UUID            PRIMARY KEY,
    organization_id     UUID            NOT NULL,
    name                VARCHAR(255)    NOT NULL,
    address             VARCHAR(500),
    latitude            DOUBLE PRECISION,
    longitude           DOUBLE PRECISION,
    created_by          VARCHAR(255)    NOT NULL,
    created_at          TIMESTAMPTZ     NOT NULL,
    updated_by          VARCHAR(255),
    updated_at          TIMESTAMPTZ,
    deleted_at          TIMESTAMPTZ
);
