CREATE TABLE events (
                        id              UUID PRIMARY KEY,
                        organizer_id    UUID            NOT NULL,
                        title           VARCHAR(200)    NOT NULL,
                        description     TEXT,
                        category        VARCHAR(100),
                        venue           INT,
                        start_time      timestamptz     NOT NULL,
                        end_time        timestamptz     NOT NULL,
                        status          VARCHAR(20)     NOT NULL DEFAULT 'DRAFT',
                        create_by       VARCHAR(20)     NOT NULL,
                        created_at      timestamptz     NOT NULL,
                        updated_by       VARCHAR(20)     NOT NULL,
                        updated_at      timestamptz     NOT NULL
);