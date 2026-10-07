-- Ticket designer v2: the ticket's own size, its background (colour, image fit and
-- placement) and free-positioned text fields. All optional: a template with none of
-- these set renders exactly as before (900 x 380, built-in layout).
--
-- ticket_width / ticket_height: pixels (100-5000); NULL = 900 x 380.
-- background_color: #RRGGBB fill under everything; NULL = white.
-- background_fit: COVER | CONTAIN | STRETCH | CUSTOM; NULL = COVER.
-- background_x/y/width/height: % of the ticket's width/height, only for CUSTOM
--   (x/y may be negative and width/height above 100: the image is clipped).
-- code_type (V26) now also accepts NONE = print no code.
ALTER TABLE ticket_templates ADD COLUMN IF NOT EXISTS ticket_width      INTEGER;
ALTER TABLE ticket_templates ADD COLUMN IF NOT EXISTS ticket_height     INTEGER;
ALTER TABLE ticket_templates ADD COLUMN IF NOT EXISTS background_color  VARCHAR(7);
ALTER TABLE ticket_templates ADD COLUMN IF NOT EXISTS background_fit    VARCHAR(10);
ALTER TABLE ticket_templates ADD COLUMN IF NOT EXISTS background_x      DOUBLE PRECISION;
ALTER TABLE ticket_templates ADD COLUMN IF NOT EXISTS background_y      DOUBLE PRECISION;
ALTER TABLE ticket_templates ADD COLUMN IF NOT EXISTS background_width  DOUBLE PRECISION;
ALTER TABLE ticket_templates ADD COLUMN IF NOT EXISTS background_height DOUBLE PRECISION;

-- Ordered text fields (sort_order = drawing order). No FK, like every other table here.
CREATE TABLE IF NOT EXISTS ticket_template_text_fields (
    template_id     UUID             NOT NULL,
    sort_order      INTEGER          NOT NULL,
    field_key       VARCHAR(20)      NOT NULL,
    x               DOUBLE PRECISION NOT NULL,
    y               DOUBLE PRECISION NOT NULL,
    font_size       DOUBLE PRECISION NOT NULL,
    color           VARCHAR(7)       NOT NULL,
    bold            BOOLEAN          NOT NULL DEFAULT FALSE,
    align           VARCHAR(6)       NOT NULL,
    rotation        INTEGER          NOT NULL DEFAULT 0,
    sample_length   INTEGER,
    sample_text     VARCHAR(30),
    text            VARCHAR(60),
    line_breaks     VARCHAR(60),
    PRIMARY KEY (template_id, sort_order)
);
