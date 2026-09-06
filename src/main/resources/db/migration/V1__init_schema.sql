CREATE TABLE events (
                        id              UUID            PRIMARY KEY,
                        organizer_id    UUID            NOT NULL,
                        title           VARCHAR(200)    NOT NULL,
                        description     TEXT,
                        category        VARCHAR(100),
                        venue           INT,
                        start_time      TIMESTAMPTZ     NOT NULL,
                        end_time        TIMESTAMPTZ     NOT NULL,
                        status          VARCHAR(20)     NOT NULL DEFAULT 'DRAFT',
                        created_by       VARCHAR(20)     NOT NULL,
                        created_at      TIMESTAMPTZ     NOT NULL,
                        updated_by      VARCHAR(20),
                        updated_at      TIMESTAMPTZ,
                        deleted_at      TIMESTAMPTZ
);