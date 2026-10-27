-- more venue details; all optional, so existing venues stay valid (null), no backfill
ALTER TABLE venues ADD COLUMN venue_type       VARCHAR(30);
ALTER TABLE venues ADD COLUMN country          VARCHAR(2);
ALTER TABLE venues ADD COLUMN province         VARCHAR(100);
ALTER TABLE venues ADD COLUMN city             VARCHAR(100);
ALTER TABLE venues ADD COLUMN description      VARCHAR(2000);
ALTER TABLE venues ADD COLUMN max_capacity     INT;
ALTER TABLE venues ADD COLUMN cover_image_url  VARCHAR(500);
