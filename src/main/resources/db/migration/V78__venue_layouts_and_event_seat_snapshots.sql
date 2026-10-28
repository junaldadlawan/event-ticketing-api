-- a venue's seat layout, saved whole by the editor; version goes up on every save
CREATE TABLE venue_layouts (
    venue_id    UUID         PRIMARY KEY,
    version     INT          NOT NULL,
    layout      JSONB        NOT NULL,
    updated_at  TIMESTAMPTZ  NOT NULL,
    updated_by  VARCHAR(255)
);

-- an event's seat map is a snapshot of the layout it copied: what the buyers' plan needs, so later edits of the venue layout never change it
ALTER TABLE seat_maps ADD COLUMN layout_version INT;
ALTER TABLE seat_maps ADD COLUMN layout_width   DOUBLE PRECISION;
ALTER TABLE seat_maps ADD COLUMN layout_height  DOUBLE PRECISION;
ALTER TABLE seat_maps ADD COLUMN layout_shapes  TEXT;

-- seats copied from a layout carry their place on the plan, their section's look and the ticket type they are sold under (all null on older seats)
ALTER TABLE seats ADD COLUMN ticket_type_id     UUID;
ALTER TABLE seats ADD COLUMN layout_section_id  UUID;
ALTER TABLE seats ADD COLUMN x                  DOUBLE PRECISION;
ALTER TABLE seats ADD COLUMN y                  DOUBLE PRECISION;
ALTER TABLE seats ADD COLUMN seat_type          VARCHAR(20);
ALTER TABLE seats ADD COLUMN section_color      VARCHAR(7);
ALTER TABLE seats ADD COLUMN section_kind       VARCHAR(20);

CREATE INDEX idx_seats_seat_map_section ON seats (seat_map_id, layout_section_id);
