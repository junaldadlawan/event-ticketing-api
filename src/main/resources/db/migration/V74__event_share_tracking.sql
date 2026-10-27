-- simple share tracking per event: a short random link code and two independent counters (no visitor data is stored)
ALTER TABLE events ADD COLUMN share_code VARCHAR(16);
ALTER TABLE events ADD COLUMN share_click_count BIGINT NOT NULL DEFAULT 0;
ALTER TABLE events ADD COLUMN share_open_count BIGINT NOT NULL DEFAULT 0;

-- existing events get a code now; events created later get theirs the first time their share link is asked for
UPDATE events SET share_code = substr(md5(random()::text || id::text), 1, 8) WHERE share_code IS NULL;

CREATE UNIQUE INDEX uq_events_share_code ON events (share_code);
