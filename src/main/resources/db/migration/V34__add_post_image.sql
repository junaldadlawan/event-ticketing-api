-- One optional picture per post: the URL of an image uploaded through POST /api/v1/uploads (this server's own
-- /api/v1/uploads/files/ URLs only, checked by the service), like users.avatar_url.
ALTER TABLE posts ADD COLUMN IF NOT EXISTS image_url VARCHAR(500);
