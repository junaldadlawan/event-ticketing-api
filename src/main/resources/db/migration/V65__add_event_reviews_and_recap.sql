-- BR-REVIEW-001: a buyer's rating (and private comment) of an event they attended. Soft-deleted, so the
-- one-review-per-user-per-event rule only covers live rows: after deleting, a user may review again.
-- No FK from event_id / user_id, like every other table here.
CREATE TABLE event_reviews (
    id          UUID            PRIMARY KEY,
    event_id    UUID            NOT NULL,
    user_id     UUID            NOT NULL,
    rating      INTEGER         NOT NULL CHECK (rating BETWEEN 1 AND 5),
    comment     VARCHAR(1000),
    created_by  VARCHAR(255)    NOT NULL,
    created_at  TIMESTAMPTZ     NOT NULL,
    updated_by  VARCHAR(255),
    updated_at  TIMESTAMPTZ,
    deleted_at  TIMESTAMPTZ
);

CREATE UNIQUE INDEX uq_event_reviews_event_user ON event_reviews (event_id, user_id) WHERE deleted_at IS NULL;

-- BR-EVENT-RECAP-001: the organizer's chat-style feed of messages and pictures for a completed event.
CREATE TABLE event_recap_messages (
    id              UUID            PRIMARY KEY,
    event_id        UUID            NOT NULL,
    author_user_id  UUID            NOT NULL,
    body            VARCHAR(2000)   NOT NULL DEFAULT '',
    image_url       VARCHAR(500),
    created_by      VARCHAR(255)    NOT NULL,
    created_at      TIMESTAMPTZ     NOT NULL,
    updated_by      VARCHAR(255),
    updated_at      TIMESTAMPTZ,
    deleted_at      TIMESTAMPTZ
);

CREATE INDEX idx_event_recap_messages_event_created ON event_recap_messages (event_id, created_at);
