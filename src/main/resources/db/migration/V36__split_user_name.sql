-- users.name becomes first_name / middle_name / last_name. Existing names are split on whitespace: the first word is
-- the first name, the last word the last name (empty for a one-word name), anything between is the middle name.
ALTER TABLE users ADD COLUMN IF NOT EXISTS first_name  VARCHAR(150);
ALTER TABLE users ADD COLUMN IF NOT EXISTS middle_name VARCHAR(150);
ALTER TABLE users ADD COLUMN IF NOT EXISTS last_name   VARCHAR(150);

UPDATE users u
SET first_name  = parts.arr[1],
    last_name   = CASE WHEN cardinality(parts.arr) >= 2 THEN parts.arr[cardinality(parts.arr)] ELSE '' END,
    middle_name = CASE WHEN cardinality(parts.arr) >= 3
                       THEN array_to_string(parts.arr[2:cardinality(parts.arr) - 1], ' ') END
FROM (SELECT id, regexp_split_to_array(trim(name), '\s+') AS arr FROM users) parts
WHERE parts.id = u.id AND u.first_name IS NULL;

ALTER TABLE users ALTER COLUMN first_name SET NOT NULL;
ALTER TABLE users ALTER COLUMN last_name SET NOT NULL;
ALTER TABLE users DROP COLUMN name;
