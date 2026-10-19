-- Event funnel analytics (BR-ANALYTICS-003): an append-only log of event page views and of what happens to cart lines (cart_items rows are
-- hard-deleted on remove, expiry and checkout, so nothing else keeps that history). No foreign keys: a log row outlives the cart line it
-- describes, and a failed log write must never be able to fail the cart change it records. Raw rows are kept 13 months.

CREATE TABLE event_views (
    id           UUID         PRIMARY KEY,
    event_id     UUID         NOT NULL,
    -- the signed-in user's id, or a SHA-256 (hex) of the browser's random visitor id; never an IP address
    visitor_key  VARCHAR(64)  NOT NULL,
    user_id      UUID,
    viewed_at    TIMESTAMPTZ  NOT NULL
);

-- de-duplication (one view per event and visitor per 30 minutes) and the funnel's range scans
CREATE INDEX idx_event_views_event_visitor ON event_views (event_id, visitor_key, viewed_at);
CREATE INDEX idx_event_views_event_time ON event_views (event_id, viewed_at);
CREATE INDEX idx_event_views_time ON event_views (viewed_at);

CREATE TABLE cart_activity (
    id              UUID         PRIMARY KEY,
    event_id        UUID         NOT NULL,
    ticket_type_id  UUID         NOT NULL,
    cart_item_id    UUID         NOT NULL,
    buyer_id        UUID         NOT NULL,
    quantity        INTEGER      NOT NULL CHECK (quantity > 0),
    action          VARCHAR(10)  NOT NULL CHECK (action IN ('ADD', 'REMOVE', 'EXPIRE', 'CHECKOUT')),
    occurred_at     TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_cart_activity_event_time ON cart_activity (event_id, occurred_at);
CREATE INDEX idx_cart_activity_time ON cart_activity (occurred_at);
