-- The venue cover image is no longer shown or set anywhere; nothing else reads this column.
ALTER TABLE venues DROP COLUMN IF EXISTS cover_image_url;
