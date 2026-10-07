-- Scheduling and hiding for posts: publish_at = not public before that moment (NULL = as soon as created),
-- expires_at = not public from that moment on (NULL = never), hidden = an admin switch that takes the post
-- off the public list regardless of the dates. Existing posts stay live exactly as before.
ALTER TABLE posts ADD COLUMN IF NOT EXISTS publish_at TIMESTAMPTZ;
ALTER TABLE posts ADD COLUMN IF NOT EXISTS expires_at TIMESTAMPTZ;
ALTER TABLE posts ADD COLUMN IF NOT EXISTS hidden BOOLEAN NOT NULL DEFAULT FALSE;
