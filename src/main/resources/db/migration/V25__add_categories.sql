-- Event categories: a managed list (admin CRUD) that events must pick from.
-- No FK from events.category by design (matches the rest of the schema); the
-- service validates the name against active categories on create/update.
CREATE TABLE categories (
    id              UUID            PRIMARY KEY,
    name            VARCHAR(100)    NOT NULL,
    slug            VARCHAR(100)    NOT NULL,
    description     VARCHAR(255),
    active          BOOLEAN         NOT NULL DEFAULT TRUE,
    sort_order      INTEGER         NOT NULL DEFAULT 0,
    created_by      VARCHAR(255)    NOT NULL,
    created_at      TIMESTAMPTZ     NOT NULL,
    updated_by      VARCHAR(255),
    updated_at      TIMESTAMPTZ,
    deleted_at      TIMESTAMPTZ
);

CREATE UNIQUE INDEX uq_categories_name_lower ON categories (lower(name));
CREATE UNIQUE INDEX uq_categories_slug ON categories (slug);

INSERT INTO categories (id, name, slug, description, active, sort_order, created_by, created_at) VALUES
    (gen_random_uuid(), 'Music',                 'music',               'Concerts, gigs and live performances',     TRUE,  10, 'System', now()),
    (gen_random_uuid(), 'Sports',                'sports',              'Games, matches and tournaments',           TRUE,  20, 'System', now()),
    (gen_random_uuid(), 'Arts & Theatre',        'arts-theatre',        'Plays, exhibitions, comedy and dance',     TRUE,  30, 'System', now()),
    (gen_random_uuid(), 'Conference',            'conference',          'Conferences, summits and expos',           TRUE,  40, 'System', now()),
    (gen_random_uuid(), 'Festival',              'festival',            'Multi-act and multi-day festivals',        TRUE,  50, 'System', now()),
    (gen_random_uuid(), 'Food & Drink',          'food-drink',          'Tastings, food fairs and dining events',   TRUE,  60, 'System', now()),
    (gen_random_uuid(), 'Nightlife',             'nightlife',           'Club nights, parties and DJ sets',         TRUE,  70, 'System', now()),
    (gen_random_uuid(), 'Community',             'community',           'Meetups, local and cultural gatherings',   TRUE,  80, 'System', now()),
    (gen_random_uuid(), 'Workshop & Education',  'workshop-education',  'Classes, workshops and training sessions', TRUE,  90, 'System', now()),
    (gen_random_uuid(), 'Family',                'family',              'Kid-friendly and family events',           TRUE, 100, 'System', now()),
    (gen_random_uuid(), 'Business & Networking', 'business-networking', 'Networking, trade and career events',      TRUE, 110, 'System', now()),
    (gen_random_uuid(), 'Charity',               'charity',             'Fundraisers and charity events',           TRUE, 120, 'System', now());

-- Keep every existing event valid: any category already in use that is not one
-- of the defaults becomes its own (active) category. Case/whitespace variants
-- of the same text collapse into one row.
INSERT INTO categories (id, name, slug, active, sort_order, created_by, created_at)
SELECT gen_random_uuid(), x.name, x.slug, TRUE, 1000, 'System', now()
FROM (
    SELECT DISTINCT ON (lower(btrim(category)))
           btrim(category) AS name,
           btrim(regexp_replace(lower(btrim(category)), '[^a-z0-9]+', '-', 'g'), '-') AS slug
    FROM events
    WHERE btrim(category) <> ''
    ORDER BY lower(btrim(category)), category
) x
WHERE x.slug <> ''
ON CONFLICT DO NOTHING;

-- Normalise existing events to the canonical spelling (e.g. 'music' -> 'Music')
-- so category filtering and the category list agree.
UPDATE events e
SET category = c.name
FROM categories c
WHERE lower(btrim(e.category)) = lower(c.name)
  AND e.category <> c.name;
