-- share tracking per day (the day is the event's own calendar day) for a line chart; counts only, nothing about the caller
CREATE TABLE share_daily_counts (
    event_id      UUID   NOT NULL,
    day           DATE   NOT NULL,
    share_button  BIGINT NOT NULL DEFAULT 0,
    copy_link     BIGINT NOT NULL DEFAULT 0,
    qr_generate   BIGINT NOT NULL DEFAULT 0,
    link_open     BIGINT NOT NULL DEFAULT 0,
    qr_scan       BIGINT NOT NULL DEFAULT 0,
    link_buy      BIGINT NOT NULL DEFAULT 0,
    qr_buy        BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_share_daily_counts_event_day UNIQUE (event_id, day)
);
