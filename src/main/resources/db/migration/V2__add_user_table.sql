CREATE TABLE users (
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    name            VARCHAR(150)    NOT NULL,
    email           VARCHAR(255)    NOT NULL,
    password_hash   VARCHAR(255)    NOT NULL,
    role            VARCHAR(20)     NOT NULL,
    created_by      TIMESTAMPTZ     NOT NULL,
    created_at      TIMESTAMPTZ     NOT NULL,
    updated_by      VARCHAR(20),
    updated_at      TIMESTAMPTZ,
    CONSTRAINT uq_users_email UNIQUE (email)
);