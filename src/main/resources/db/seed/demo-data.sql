-- =============================================================================
-- DEMO DATA: demo accounts, one organization and one sample event
-- DEV / DEMO ONLY - DO NOT RUN AGAINST STAGING OR PRODUCTION
-- =============================================================================
-- What it creates
--   * 2 admins                                   (users.role = ADMIN)
--   * 5 customers                                (users.role = CUSTOMER)
--   * 1 organization "Demo Events Co." (APPROVED) with
--       - 1 owner      (CUSTOMER account + organization role OWNER)
--       - 1 organizer  (CUSTOMER account + organization role ORGANIZER)
--       - 1 scanner    (CUSTOMER account + organization role CHECK_IN_STAFF)
--   * 1 sample event "Demo Sunset Concert" (ON_SALE, starts in 30 days) with two ticket types
--       (General Admission $50.00 and VIP $120.00)
--
-- The owner, organizer and scanner are three extra accounts, so 10 accounts in all. "Organizer", "scanner" and
-- "organization owner" are not account types: users.role only has CUSTOMER / ADMIN, and these three are
-- organization_member_roles memberships on top of a CUSTOMER account (see user/enums/Role.java).
--
-- Passwords (each user has its own, written as a "password:" comment next to the user; every password_hash is a real
-- BCrypt hash of it, made with this project's own BCryptPasswordEncoder):
--
--   admin1@demo.test      Admin1@Demo        customer1@demo.test   Customer1@Demo
--   admin2@demo.test      Admin2@Demo        customer2@demo.test   Customer2@Demo
--   owner@demo.test       Owner@Demo1        customer3@demo.test   Customer3@Demo
--   organizer@demo.test   Organizer@Demo1    customer4@demo.test   Customer4@Demo
--   scanner@demo.test     Scanner@Demo1      customer5@demo.test   Customer5@Demo
--
-- This is NOT a Flyway migration: it lives outside src/main/resources/db/migration on purpose, so Flyway never runs it
-- (a migration would put these known passwords on every environment). Run it by hand against a local dev database,
-- after the app has booted once so Flyway has created the schema:
--
--   PowerShell:  Get-Content src\main\resources\db\seed\demo-data.sql | docker compose exec -T postgresql psql -U user -d event_ticketing -v ON_ERROR_STOP=1
--   bash:        docker compose exec -T postgresql psql -U user -d event_ticketing -v ON_ERROR_STOP=1 < src/main/resources/db/seed/demo-data.sql
--
-- Safe to run more than once: it only INSERTs, every insert skips what already exists (ON CONFLICT / NOT EXISTS), and
-- the whole script is one transaction, so a failure leaves nothing half-done. It never updates or deletes anything.
-- If an account with one of these emails already exists it is left as it is (its password is NOT changed) and the
-- organization is linked to that existing account.
--
-- Works with both user-table shapes: the current one (first_name / middle_name / last_name / birth_date /
-- phone_number) and the older one with a single "name" column.
-- =============================================================================

BEGIN;

-- The 10 accounts, in one place. Plain-text password for each is in the comment above its row.
CREATE TEMP TABLE demo_user (
    email         text PRIMARY KEY,
    first_name    text NOT NULL,
    middle_name   text,
    last_name     text NOT NULL,
    birth_date    date NOT NULL,
    phone_number  text,
    role          text NOT NULL,
    password_hash text NOT NULL
) ON COMMIT DROP;

INSERT INTO demo_user (email, first_name, middle_name, last_name, birth_date, phone_number, role, password_hash) VALUES
    -- Avery Santos, platform admin | password: Admin1@Demo
    ('admin1@demo.test',    'Avery',  NULL,    'Santos',     DATE '1988-03-14', '+639171000001', 'ADMIN',    '$2a$10$OsjKeMh6b4tj9APy2YTh7.yYFhExtp7JLogTj.535zUhZLTxJPGFi'),
    -- Morgan Reyes, platform admin | password: Admin2@Demo
    ('admin2@demo.test',    'Morgan', NULL,    'Reyes',      DATE '1990-07-22', '+639171000002', 'ADMIN',    '$2a$10$jwU2P4LQeLKvhWZoUxQUtuLIT7CgSi8hwjFQIt9t9bRJp0UriBahS'),
    -- Jamie Cruz, customer | password: Customer1@Demo
    ('customer1@demo.test', 'Jamie',  NULL,    'Cruz',       DATE '1995-01-09', '+639171000011', 'CUSTOMER', '$2a$10$OQV5cmCJM5pSDloJNAQzUecEyeAEegCzZzdwUGzYgzdzdtJ6Kgi3.'),
    -- Taylor Lim, customer | password: Customer2@Demo
    ('customer2@demo.test', 'Taylor', NULL,    'Lim',        DATE '1997-11-30', '+639171000012', 'CUSTOMER', '$2a$10$B3DbHtvlRiLSfnX2YU2GMuuacdTJmdPS8hYWa0xiJeM36tXoEUGri'),
    -- Riley Park, customer | password: Customer3@Demo
    ('customer3@demo.test', 'Riley',  NULL,    'Park',       DATE '1992-05-18', '+639171000013', 'CUSTOMER', '$2a$10$5eDwIBtG9y9RRgcTLNGufuwghwyMpOyi5MmKGuFcNFAQl/ErmKCk6'),
    -- Casey Tan, customer | password: Customer4@Demo
    ('customer4@demo.test', 'Casey',  NULL,    'Tan',        DATE '2000-09-03', '+639171000014', 'CUSTOMER', '$2a$10$CW.PGkCijvvkeJ8RkF8M3euNPczXImosNWW2MX1Ck7HoSeidkyonK'),
    -- Jordan Dela Cruz, customer | password: Customer5@Demo
    ('customer5@demo.test', 'Jordan', NULL,    'Dela Cruz',  DATE '1999-12-25', '+639171000015', 'CUSTOMER', '$2a$10$vuCerSOQgQYabkxJFKVeY.7RRQ8pfqazF4UO03ShcAw9IEjZy/aiG'),
    -- Owen Mercado, owner of "Demo Events Co." | password: Owner@Demo1
    ('owner@demo.test',     'Owen',   'Dela',  'Mercado',    DATE '1985-06-12', '+639171000021', 'CUSTOMER', '$2a$10$F3fOaRxdWOtzaYCIkyc.ru4gZXEHW3kNqLrAGxybgEIU/mvoHnS5O'),
    -- Paula Ramos, organizer of "Demo Events Co." | password: Organizer@Demo1
    ('organizer@demo.test', 'Paula',  NULL,    'Ramos',      DATE '1991-02-27', '+639171000022', 'CUSTOMER', '$2a$10$UX7RV1bBKBxRQY9lP/CUbe2ZeSsq/iohOfHyU902kTVgWDRuevvUC'),
    -- Sam Villanueva, door scanner (check-in staff) of "Demo Events Co." | password: Scanner@Demo1
    ('scanner@demo.test',   'Sam',    NULL,    'Villanueva', DATE '1996-08-08', '+639171000023', 'CUSTOMER', '$2a$10$4TWbfRxQt58mVwrTtFJ0du.T3/fkJYxAANSCS8Hmig0PpA6yshYYa');

-- Insert into whichever users table shape this database has (PL/pgSQL only plans the branch that runs).
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_schema = current_schema() AND table_name = 'users' AND column_name = 'first_name') THEN
        INSERT INTO users (id, first_name, middle_name, last_name, birth_date, phone_number, email, password_hash,
                           role, account_status, created_by, created_at)
        SELECT gen_random_uuid(), d.first_name, d.middle_name, d.last_name, d.birth_date, d.phone_number, d.email,
               d.password_hash, d.role, 'ACTIVE', 'Demo Seed', now()
        FROM demo_user d
        ON CONFLICT DO NOTHING;
    ELSE
        INSERT INTO users (id, name, email, password_hash, role, account_status, created_by, created_at)
        SELECT gen_random_uuid(), concat_ws(' ', d.first_name, d.middle_name, d.last_name), d.email,
               d.password_hash, d.role, 'ACTIVE', 'Demo Seed', now()
        FROM demo_user d
        ON CONFLICT DO NOTHING;
    END IF;
END $$;

-- ---------------------------------------------------------------------------
-- The organization: approved, owned by owner@demo.test
-- ---------------------------------------------------------------------------
INSERT INTO organizations (id, name, status, owner_id, created_by, created_at)
SELECT 'd0000000-0000-4000-8000-000000000001', 'Demo Events Co.', 'APPROVED', u.id, 'Demo Seed', now()
FROM users u
WHERE u.email = 'owner@demo.test'
ON CONFLICT DO NOTHING;

-- Its three members and their organization roles (looked up by email, so an existing account is reused).
INSERT INTO organization_members (user_id, organization_id, assigned_at)
SELECT u.id, 'd0000000-0000-4000-8000-000000000001', now()
FROM users u
WHERE u.email IN ('owner@demo.test', 'organizer@demo.test', 'scanner@demo.test')
ON CONFLICT DO NOTHING;

INSERT INTO organization_member_roles (user_id, organization_id, role)
SELECT u.id, 'd0000000-0000-4000-8000-000000000001', r.role
FROM (VALUES ('owner@demo.test',     'OWNER'),
             ('organizer@demo.test', 'ORGANIZER'),
             ('scanner@demo.test',   'CHECK_IN_STAFF')) AS r(email, role)
JOIN users u ON u.email = r.email
ON CONFLICT DO NOTHING;

-- ---------------------------------------------------------------------------
-- The sample event (on sale, 30 days from now) and its two ticket types
-- ---------------------------------------------------------------------------
INSERT INTO events (id, organization_id, title, description, category, start_time, end_time, status, ticket_prefix,
                    timezone, created_by, created_at)
VALUES ('d0000000-0000-4000-8000-000000000002', 'd0000000-0000-4000-8000-000000000001', 'Demo Sunset Concert',
        'An open-air evening of live music as the sun goes down. Sample event created by the demo seed script.',
        'Music', now() + interval '30 days', now() + interval '30 days 4 hours', 'ON_SALE', 'DMO',
        'Asia/Manila', 'Demo Seed', now())
ON CONFLICT DO NOTHING;

INSERT INTO ticket_types (id, event_id, name, kind, price_amount, price_currency, quantity_total, quantity_available,
                          sale_start_at, sale_end_at, max_per_order, position, sales_paused, created_by, created_at)
VALUES ('d0000000-0000-4000-8000-000000000003', 'd0000000-0000-4000-8000-000000000002', 'General Admission',
        'GENERAL_ADMISSION', 5000, 'USD', 200, 200, now() - interval '1 day', now() + interval '30 days', 8, 0, FALSE,
        'Demo Seed', now()),
       ('d0000000-0000-4000-8000-000000000004', 'd0000000-0000-4000-8000-000000000002', 'VIP',
        'GENERAL_ADMISSION', 12000, 'USD', 50, 50, now() - interval '1 day', now() + interval '30 days', 4, 1, FALSE,
        'Demo Seed', now())
ON CONFLICT DO NOTHING;

COMMIT;

-- What is there now (counts for the demo accounts, the organization and the event):
SELECT 'admins'                 AS item, count(*) AS total FROM users WHERE email LIKE '%@demo.test' AND role = 'ADMIN'
UNION ALL SELECT 'customers (incl. the 3 org members)', count(*) FROM users WHERE email LIKE '%@demo.test' AND role = 'CUSTOMER'
UNION ALL SELECT 'organization members',                 count(*) FROM organization_members WHERE organization_id = 'd0000000-0000-4000-8000-000000000001'
UNION ALL SELECT 'events',                               count(*) FROM events WHERE id = 'd0000000-0000-4000-8000-000000000002'
UNION ALL SELECT 'ticket types',                         count(*) FROM ticket_types WHERE event_id = 'd0000000-0000-4000-8000-000000000002';
