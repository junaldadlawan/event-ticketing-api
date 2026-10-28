-- an organization's seat layouts, no longer owned by a venue; layout holds the whole design (width, height, sections, shapes) as the editor saves it
CREATE TABLE seat_layouts (
    id               UUID              PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id  UUID              NOT NULL REFERENCES organizations (id),
    name             VARCHAR(100)      NOT NULL,
    width            DOUBLE PRECISION  NOT NULL,
    height           DOUBLE PRECISION  NOT NULL,
    seat_count       INT               NOT NULL,
    version          INT               NOT NULL,
    layout           JSONB             NOT NULL,
    created_by       VARCHAR(255)      NOT NULL,
    created_at       TIMESTAMPTZ       NOT NULL,
    updated_by       VARCHAR(255),
    updated_at       TIMESTAMPTZ,
    deleted_at       TIMESTAMPTZ
);
CREATE UNIQUE INDEX ux_seat_layouts_org_name ON seat_layouts (organization_id, lower(name)) WHERE deleted_at IS NULL;

-- layouts an admin offers every organizer to copy
CREATE TABLE layout_presets (
    id           UUID              PRIMARY KEY DEFAULT gen_random_uuid(),
    name         VARCHAR(100)      NOT NULL,
    description  VARCHAR(500),
    active       BOOLEAN           NOT NULL,
    width        DOUBLE PRECISION  NOT NULL,
    height       DOUBLE PRECISION  NOT NULL,
    seat_count   INT               NOT NULL,
    version      INT               NOT NULL,
    layout       JSONB             NOT NULL,
    created_by   VARCHAR(255)      NOT NULL,
    created_at   TIMESTAMPTZ       NOT NULL,
    updated_by   VARCHAR(255),
    updated_at   TIMESTAMPTZ,
    deleted_at   TIMESTAMPTZ
);
CREATE UNIQUE INDEX ux_layout_presets_name ON layout_presets (lower(name)) WHERE deleted_at IS NULL;

-- the layout an event's reserved seats come from
ALTER TABLE events ADD COLUMN layout_id UUID REFERENCES seat_layouts (id);
CREATE INDEX idx_events_layout_id ON events (layout_id);

-- every venue layout becomes an organization layout named after its venue (a second venue of the same name gets " (2)" ...), keeping its version so
-- events that already copied seats can still add the other sections; seat maps and seats are not touched
CREATE TEMP TABLE venue_layout_moves ON COMMIT DROP AS
SELECT gen_random_uuid() AS layout_id, v.id AS venue_id, v.organization_id, vl.version, vl.layout, vl.updated_at, vl.updated_by,
       left(trim(v.name), 90) AS base_name,
       row_number() OVER (PARTITION BY v.organization_id, lower(left(trim(v.name), 90)) ORDER BY v.created_at, v.id) AS n
FROM venue_layouts vl
JOIN venues v ON v.id = vl.venue_id;

INSERT INTO seat_layouts (id, organization_id, name, width, height, seat_count, version, layout, created_by, created_at, updated_by, updated_at)
SELECT m.layout_id, m.organization_id,
       CASE WHEN m.n = 1 THEN m.base_name ELSE m.base_name || ' (' || m.n || ')' END,
       (m.layout ->> 'width')::DOUBLE PRECISION, (m.layout ->> 'height')::DOUBLE PRECISION,
       (SELECT count(*) FROM jsonb_array_elements(m.layout -> 'sections') s, jsonb_array_elements(s -> 'rows') r, jsonb_array_elements(r -> 'seats') seat),
       m.version, m.layout, coalesce(m.updated_by, 'System Audit'), m.updated_at, m.updated_by, m.updated_at
FROM venue_layout_moves m;

UPDATE events e SET layout_id = m.layout_id FROM venue_layout_moves m WHERE e.venue_id = m.venue_id;
