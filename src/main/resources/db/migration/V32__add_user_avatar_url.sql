-- The user's profile picture: the URL of an image uploaded through POST /api/v1/uploads (NULL = none, the
-- web app falls back to the user's initials).
ALTER TABLE users ADD COLUMN IF NOT EXISTS avatar_url VARCHAR(500);
