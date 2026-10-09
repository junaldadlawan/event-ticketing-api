-- Admin-written posts: announcements and sales, either site-wide (event_id NULL, shown on Home) or for one
-- event (shown on its landing page). Read by everyone, written and removed by admins only; soft-deleted.
-- No FK from event_id, like every other table here.
CREATE TABLE posts (
    id          UUID            PRIMARY KEY,
    event_id    UUID,
    kind        VARCHAR(20)     NOT NULL,
    title       VARCHAR(120)    NOT NULL,
    body        VARCHAR(2000)   NOT NULL DEFAULT '',
    created_by  VARCHAR(255)    NOT NULL,
    created_at  TIMESTAMPTZ     NOT NULL,
    updated_by  VARCHAR(255),
    updated_at  TIMESTAMPTZ,
    deleted_at  TIMESTAMPTZ
);

CREATE INDEX idx_posts_event_created ON posts (event_id, created_at DESC);
