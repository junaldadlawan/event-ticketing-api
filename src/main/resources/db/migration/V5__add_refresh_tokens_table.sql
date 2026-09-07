CREATE TABLE refresh_tokens (
    id          UUID            PRIMARY KEY,
    user_id     UUID            NOT NULL,
    expires_at  TIMESTAMPTZ     NOT NULL,
    revoked     BOOLEAN         NOT NULL DEFAULT FALSE,
    created_by  VARCHAR(255)    NOT NULL,
    created_at  TIMESTAMPTZ     NOT NULL,
    updated_by  VARCHAR(255),
    updated_at  TIMESTAMPTZ,
    deleted_at  TIMESTAMPTZ
);
