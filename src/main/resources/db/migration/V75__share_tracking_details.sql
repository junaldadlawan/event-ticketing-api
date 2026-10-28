-- share tracking in detail: what is clicked in the Share window, how the shared link is opened (link or QR) and buy clicks from those visits
ALTER TABLE events ADD COLUMN share_copy_count BIGINT NOT NULL DEFAULT 0;
ALTER TABLE events ADD COLUMN share_qr_count BIGINT NOT NULL DEFAULT 0;
ALTER TABLE events ADD COLUMN share_link_open_count BIGINT NOT NULL DEFAULT 0;
ALTER TABLE events ADD COLUMN share_qr_scan_count BIGINT NOT NULL DEFAULT 0;
ALTER TABLE events ADD COLUMN share_link_buy_count BIGINT NOT NULL DEFAULT 0;
ALTER TABLE events ADD COLUMN share_qr_buy_count BIGINT NOT NULL DEFAULT 0;

-- every open counted so far came through the plain link; share_open_count is no longer written (opens = link opens + QR scans)
UPDATE events SET share_link_open_count = share_open_count WHERE share_open_count > 0;
