-- share tracking per hour of the day (in the event's own timezone), kept next to the daily rows; counts only, nothing about the caller.
-- Counts from before this table existed have no hour and are not spread over the hours.
CREATE TABLE share_hourly_counts (
    event_id      UUID     NOT NULL,
    day           DATE     NOT NULL,
    hour          SMALLINT NOT NULL,
    share_button  BIGINT   NOT NULL DEFAULT 0,
    copy_link     BIGINT   NOT NULL DEFAULT 0,
    qr_generate   BIGINT   NOT NULL DEFAULT 0,
    link_open     BIGINT   NOT NULL DEFAULT 0,
    qr_scan       BIGINT   NOT NULL DEFAULT 0,
    link_buy      BIGINT   NOT NULL DEFAULT 0,
    qr_buy        BIGINT   NOT NULL DEFAULT 0,
    CONSTRAINT uq_share_hourly_counts_event_day_hour UNIQUE (event_id, day, hour),
    CONSTRAINT ck_share_hourly_counts_hour CHECK (hour BETWEEN 0 AND 23)
);
