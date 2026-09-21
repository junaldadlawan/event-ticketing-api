-- =============================================================================
-- DEV-ONLY SEED DATA — DO NOT RUN AGAINST STAGING OR PRODUCTION
-- =============================================================================
-- Purpose: one fully-populated row per table, cross-linked with a small set
-- of hardcoded UUIDs, so a new team member can spin up a local Postgres,
-- run this script, and immediately have real data to hit endpoints against
-- instead of manually walking the whole event/checkout flow by hand.
--
-- This is NOT a Flyway migration — it lives outside src/main/resources/db/
-- migration/ on purpose, so Flyway (classpath:db/migration by default)
-- never picks it up. It must only ever be run manually, by a human, against
-- a local dev database.
--
-- How to run (after `docker compose up -d` and letting the app boot once
-- so Flyway applies V1-V24):
--   docker compose exec -T postgresql psql -U user -d event_ticketing -v ON_ERROR_STOP=1 < src/main/resources/db/seed/dev-seed.sql
--
-- The whole script runs as one transaction (BEGIN/COMMIT below), so a
-- failure partway through rolls back cleanly instead of leaving partial
-- seed data.
--
-- Re-running: several columns are UNIQUE (users.email, events.ticket_prefix,
-- tickets.credential, etc.), so re-running this script against a database
-- that already has this seed data WILL fail on those constraints. To start
-- clean: `docker compose down -v && docker compose up -d`, let the app
-- re-migrate, then re-run this script.
--
-- Five distinct user personas, one of each kind (all share the same test
-- password — each password_hash is still its own real bcrypt hash of it,
-- generated with this project's own BCryptPasswordEncoder):
--   password: Password123!
--
--   alex.chen@example.com     - ADMIN                   platform admin
--   jordan.rivera@example.com - CUSTOMER                 regular user: the
--                                buyer/ticket-owner throughout this script
--   taylor.brooks@example.com - CUSTOMER + org role OWNER          owns
--                                the seed organization (organizations.owner_id)
--   morgan.lee@example.com    - CUSTOMER + org role ORGANIZER      manages
--                                the event day-to-day (venue/event/ticket
--                                type/promo/template/policy created_by)
--   casey.kim@example.com     - CUSTOMER + org role CHECK_IN_STAFF the
--                                door scanner (scanner_devices created_by)
--
-- `users.role` itself only has two values (CUSTOMER/ADMIN — see
-- user/enums/Role.java); "organizer"/"scanner"/"organization owner" are not
-- separate account types, they're organization_member_roles memberships
-- (OWNER/ORGANIZER/CHECK_IN_STAFF) layered on top of a CUSTOMER account —
-- same as how this would really work.
--
-- Simplification: this schema has NO real FK constraints anywhere (every
-- relationship is a business-key convention only — see the V6/V7/V8/V9
-- migration comments), and every table besides `users` and
-- `organization_members`/`organization_member_roles` gets exactly one row.
-- So a couple of columns that would realistically reference yet another
-- distinct user in a fuller dataset (ticket_transfers' from/to user) still
-- reuse the single "regular user" persona. Called out inline below.
--
-- UUID map (one constant per seeded entity, reused everywhere it's
-- logically referenced):
--   ...0001  users                (Jordan Rivera — CUSTOMER — regular user / buyer)
--   ...0033  users                (Alex Chen — ADMIN)
--   ...0034  users                (Taylor Brooks — CUSTOMER — organization owner)
--   ...0035  users                (Morgan Lee — CUSTOMER — organizer)
--   ...0036  users                (Casey Kim — CUSTOMER — check-in scanner)
--   ...0002  organizations        (Seed Events Co.)
--   ...0003  venues               (Seed Arena)
--   ...0004  events               (Sunset Music Festival)
--   ...0005  ticket_types         (General Admission)
--   ...0006  seat_maps
--   ...0007  seats                (Section A, Row 1, Seat 12)
--   ...0008  promo_codes          (WELCOME10)
--   ...0009  carts
--   ...0010  cart_items
--   ...0011  orders
--   ...0012  payments
--   ...0013  checkout_idempotency_keys
--   ...0014  tickets
--   ...0015  ticket_templates
--   ...0016  ticket_transfers
--   ...0017  resale_policies
--   ...0018  resale_listings
--   ...0019  resale_purchase_idempotency_keys
--   ...0020  refund_policies
--   ...0021  refunds
--   ...0022  payouts
--   ...0023  waitlist_entries
--   ...0024  check_in_configs
--   ...0025  scanner_devices
--   ...0026  check_in_records
--   ...0027  fallback_scan_records
--   ...0028  notifications
--   ...0029  disputes
--   ...0030  moderation_actions
--   ...0031  audit_log_entries
--   ...0032  refresh_tokens
-- =============================================================================

BEGIN;

-- 1. users — one of each persona
INSERT INTO users (id, name, email, password_hash, role, account_status, created_by, created_at, updated_by, updated_at, deleted_at)
VALUES
(
    '00000000-0000-0000-0000-000000000001',
    'Jordan Rivera',
    'jordan.rivera@example.com',
    '$2a$10$73wCgtgzhx.qha4wVv2ku.KT3oOZJ5ShH6bVpjBsTGi06MgP1IiXS', -- bcrypt hash of "Password123!"
    'CUSTOMER',
    'ACTIVE',
    'seed-script',
    now(),
    'seed-script',
    now(),
    NULL
),
(
    '00000000-0000-0000-0000-000000000033',
    'Alex Chen',
    'alex.chen@example.com',
    '$2a$10$hDgyLvpaTlxn9i4MaInj3OfBVezc9.dJUepfEXXCRl9M1Ax8PXL8G', -- bcrypt hash of "Password123!"
    'ADMIN',
    'ACTIVE',
    'seed-script',
    now(),
    'seed-script',
    now(),
    NULL
),
(
    '00000000-0000-0000-0000-000000000034',
    'Taylor Brooks',
    'taylor.brooks@example.com',
    '$2a$10$5xHqJx5a79cM/Te6KE/yCu7iC8L1HGG/WGA8ji1r2TX3PujdZ3xI6', -- bcrypt hash of "Password123!"
    'CUSTOMER',
    'ACTIVE',
    'seed-script',
    now(),
    'seed-script',
    now(),
    NULL
),
(
    '00000000-0000-0000-0000-000000000035',
    'Morgan Lee',
    'morgan.lee@example.com',
    '$2a$10$KpISuVD/J3tvlSAYRimQEu6sisu56/XKhcShk1.BtCIJvvcNIs1AW', -- bcrypt hash of "Password123!"
    'CUSTOMER',
    'ACTIVE',
    'seed-script',
    now(),
    'seed-script',
    now(),
    NULL
),
(
    '00000000-0000-0000-0000-000000000036',
    'Casey Kim',
    'casey.kim@example.com',
    '$2a$10$J3bEdvsBWoRkf9G.f8GHLe5J6UlyEXZ57X9iSpwiEQDSHwyj/4dCW', -- bcrypt hash of "Password123!"
    'CUSTOMER',
    'ACTIVE',
    'seed-script',
    now(),
    'seed-script',
    now(),
    NULL
);

-- 2. organizations — owned by Taylor Brooks (organization owner persona)
INSERT INTO organizations (id, name, status, owner_id, rejection_reason, created_by, created_at, updated_by, updated_at, deleted_at)
VALUES (
    '00000000-0000-0000-0000-000000000002',
    'Seed Events Co.',
    'APPROVED',
    '00000000-0000-0000-0000-000000000034', -- Taylor Brooks (owner)
    NULL, -- only meaningful when status = REJECTED
    '00000000-0000-0000-0000-000000000034',
    now(),
    '00000000-0000-0000-0000-000000000034',
    now(),
    NULL
);

-- 3. organization_documents (child collection of organizations)
INSERT INTO organization_documents (organization_id, sort_order, type, url)
VALUES (
    '00000000-0000-0000-0000-000000000002',
    0,
    'BUSINESS_LICENSE',
    'https://example.com/seed/org-business-license.pdf'
);

-- 4. organization_members — the three staff personas (owner, organizer,
-- check-in scanner) all belong to the seed organization. The regular user
-- (Jordan Rivera) does NOT - they're a customer, not org staff.
INSERT INTO organization_members (user_id, organization_id, assigned_at)
VALUES
    ('00000000-0000-0000-0000-000000000034', '00000000-0000-0000-0000-000000000002', now()), -- Taylor Brooks
    ('00000000-0000-0000-0000-000000000035', '00000000-0000-0000-0000-000000000002', now()), -- Morgan Lee
    ('00000000-0000-0000-0000-000000000036', '00000000-0000-0000-0000-000000000002', now()); -- Casey Kim

-- 5. organization_member_roles (child collection of organization_members)
-- - this is what actually makes "organizer"/"scanner"/"owner" distinct
-- personas rather than three identical customer accounts.
INSERT INTO organization_member_roles (user_id, organization_id, role)
VALUES
    ('00000000-0000-0000-0000-000000000034', '00000000-0000-0000-0000-000000000002', 'OWNER'),
    ('00000000-0000-0000-0000-000000000035', '00000000-0000-0000-0000-000000000002', 'ORGANIZER'),
    ('00000000-0000-0000-0000-000000000036', '00000000-0000-0000-0000-000000000002', 'CHECK_IN_STAFF');

-- 6. venues — managed by Morgan Lee (organizer)
INSERT INTO venues (id, organization_id, name, address, latitude, longitude, created_by, created_at, updated_by, updated_at, deleted_at)
VALUES (
    '00000000-0000-0000-0000-000000000003',
    '00000000-0000-0000-0000-000000000002',
    'Seed Arena',
    '123 Test Street, Springfield',
    39.7817,
    -89.6501,
    '00000000-0000-0000-0000-000000000035', -- Morgan Lee (organizer)
    now(),
    '00000000-0000-0000-0000-000000000035',
    now(),
    NULL
);

-- 7. events — managed by Morgan Lee (organizer)
INSERT INTO events (id, organization_id, title, description, category, venue_id, start_time, end_time, timezone, status, ticket_prefix, created_by, created_at, updated_by, updated_at, deleted_at)
VALUES (
    '00000000-0000-0000-0000-000000000004',
    '00000000-0000-0000-0000-000000000002',
    'Sunset Music Festival',
    'A seed test event for local development.',
    'Music',
    '00000000-0000-0000-0000-000000000003',
    now() + interval '30 days',
    now() + interval '30 days' + interval '5 hours',
    'America/New_York',
    'ON_SALE',
    'SMF', -- events.ticket_prefix is VARCHAR(3)
    'seed-script', -- events.created_by is VARCHAR(20), unlike every other table's VARCHAR(255) - can't fit a UUID
    now(),
    'seed-script',
    now(),
    NULL
);

-- 8. event_images (child collection of events)
INSERT INTO event_images (event_id, sort_order, url)
VALUES (
    '00000000-0000-0000-0000-000000000004',
    0,
    'https://example.com/seed/event-cover.jpg'
);

-- 9. ticket_types — managed by Morgan Lee (organizer)
INSERT INTO ticket_types (id, event_id, name, kind, price_amount, price_currency, quantity_total, quantity_available, sale_start_at, sale_end_at, max_per_order, created_by, created_at, updated_by, updated_at, deleted_at)
VALUES (
    '00000000-0000-0000-0000-000000000005',
    '00000000-0000-0000-0000-000000000004',
    'General Admission',
    'RESERVED_SEATING',
    5000, -- $50.00 in minor units
    'USD',
    100,
    99, -- one sold, matching the one seeded ticket below
    now() - interval '7 days',
    now() + interval '30 days',
    10,
    '00000000-0000-0000-0000-000000000035', -- Morgan Lee (organizer)
    now(),
    '00000000-0000-0000-0000-000000000035',
    now(),
    NULL
);

-- 10. seat_maps — managed by Morgan Lee (organizer)
INSERT INTO seat_maps (id, event_id, created_by, created_at, updated_by, updated_at, deleted_at)
VALUES (
    '00000000-0000-0000-0000-000000000006',
    '00000000-0000-0000-0000-000000000004',
    '00000000-0000-0000-0000-000000000035',
    now(),
    '00000000-0000-0000-0000-000000000035',
    now(),
    NULL
);

-- 11. seats
INSERT INTO seats (id, seat_map_id, section, row, seat_number, status, created_at, updated_at)
VALUES (
    '00000000-0000-0000-0000-000000000007',
    '00000000-0000-0000-0000-000000000006',
    'A',
    '1',
    '12',
    'SOLD', -- matches the seeded ticket below
    now(),
    now()
);

-- 12. promo_codes — managed by Morgan Lee (organizer)
INSERT INTO promo_codes (id, event_id, code, discount_type, discount_value, usage_limit_total, usage_limit_per_buyer, valid_from, valid_until, created_by, created_at, updated_by, updated_at, deleted_at)
VALUES (
    '00000000-0000-0000-0000-000000000008',
    '00000000-0000-0000-0000-000000000004',
    'WELCOME10',
    'PERCENTAGE',
    10.00,
    100,
    1,
    now() - interval '7 days',
    now() + interval '60 days',
    '00000000-0000-0000-0000-000000000035',
    now(),
    '00000000-0000-0000-0000-000000000035',
    now(),
    NULL
);

-- 13. promo_code_applicable_ticket_types (child collection of promo_codes)
INSERT INTO promo_code_applicable_ticket_types (promo_code_id, ticket_type_id)
VALUES (
    '00000000-0000-0000-0000-000000000008',
    '00000000-0000-0000-0000-000000000005'
);

-- 14. carts — Jordan Rivera (regular user) is the buyer
INSERT INTO carts (id, buyer_id, promo_code_id, created_at, updated_at)
VALUES (
    '00000000-0000-0000-0000-000000000009',
    '00000000-0000-0000-0000-000000000001',
    '00000000-0000-0000-0000-000000000008',
    now(),
    now()
);

-- 15. cart_items
INSERT INTO cart_items (id, cart_id, ticket_type_id, seat_id, quantity, hold_expires_at, created_at)
VALUES (
    '00000000-0000-0000-0000-000000000010',
    '00000000-0000-0000-0000-000000000009',
    '00000000-0000-0000-0000-000000000005',
    '00000000-0000-0000-0000-000000000007',
    1,
    now() + interval '15 minutes',
    now()
);

-- 16. orders — Jordan Rivera (regular user) is the buyer
INSERT INTO orders (id, buyer_id, payee_type, payee_id, status, promo_code, cart_id, total_amount, total_currency, created_by, created_at, updated_at)
VALUES (
    '00000000-0000-0000-0000-000000000011',
    '00000000-0000-0000-0000-000000000001',
    'ORGANIZATION',
    '00000000-0000-0000-0000-000000000002',
    'PAID',
    'WELCOME10',
    '00000000-0000-0000-0000-000000000009',
    4500, -- $50.00 - 10% WELCOME10 discount
    'USD',
    '00000000-0000-0000-0000-000000000001',
    now(),
    now()
);

-- 17. payments
INSERT INTO payments (id, order_id, gateway_ref, amount_amount, amount_currency, status, created_at, updated_at)
VALUES (
    '00000000-0000-0000-0000-000000000012',
    '00000000-0000-0000-0000-000000000011',
    'seed-gateway-ref-0001',
    4500,
    'USD',
    'COMPLETED',
    now(),
    now()
);

-- 18. checkout_idempotency_keys (id is caller-supplied, not generated) — Jordan Rivera (regular user)
INSERT INTO checkout_idempotency_keys (id, buyer_id, cart_id, order_id, created_at)
VALUES (
    '00000000-0000-0000-0000-000000000013',
    '00000000-0000-0000-0000-000000000001',
    '00000000-0000-0000-0000-000000000009',
    '00000000-0000-0000-0000-000000000011',
    now()
);

-- 19. tickets (id is app-assigned, not generated) — owned by Jordan Rivera (regular user)
INSERT INTO tickets (id, order_id, event_id, ticket_type_id, seat_id, owner_id, ticket_number, credential, credential_version, face_value_amount, face_value_currency, status, created_at, updated_at)
VALUES (
    '00000000-0000-0000-0000-000000000014',
    '00000000-0000-0000-0000-000000000011',
    '00000000-0000-0000-0000-000000000004',
    '00000000-0000-0000-0000-000000000005',
    '00000000-0000-0000-0000-000000000007',
    '00000000-0000-0000-0000-000000000001',
    'SMF-000001',
    'seed-credential-token-0001', -- never exposed via API; must be unique
    0,
    5000,
    'USD',
    'VALID',
    now(),
    now()
);

-- 20. ticket_templates — managed by Morgan Lee (organizer)
INSERT INTO ticket_templates (id, event_id, ticket_type_id, format, logo_url, background_image_url, primary_color, created_by, created_at, updated_by, updated_at, deleted_at)
VALUES (
    '00000000-0000-0000-0000-000000000015',
    '00000000-0000-0000-0000-000000000004',
    '00000000-0000-0000-0000-000000000005',
    'DIGITAL',
    'https://example.com/seed/logo.png',
    'https://example.com/seed/ticket-background.png',
    '#1E90FF',
    '00000000-0000-0000-0000-000000000035',
    now(),
    '00000000-0000-0000-0000-000000000035',
    now(),
    NULL
);

-- 21. ticket_transfers
-- Simplification: from_user_id and to_user_id both reuse the single
-- regular-user persona (this schema has no FK enforcing they differ) — a
-- fuller dataset would use two distinct customer accounts here.
INSERT INTO ticket_transfers (id, ticket_id, from_user_id, to_user_id, source, transferred_at)
VALUES (
    '00000000-0000-0000-0000-000000000016',
    '00000000-0000-0000-0000-000000000014',
    '00000000-0000-0000-0000-000000000001',
    '00000000-0000-0000-0000-000000000001',
    'DIRECT_TRANSFER',
    now() - interval '2 days'
);

-- 22. resale_policies — managed by Morgan Lee (organizer)
INSERT INTO resale_policies (id, event_id, enabled, price_cap_rule, fee_amount, fee_currency, created_by, created_at, updated_by, updated_at, deleted_at)
VALUES (
    '00000000-0000-0000-0000-000000000017',
    '00000000-0000-0000-0000-000000000004',
    TRUE,
    'FACE_VALUE_PLUS_FEE',
    500, -- $5.00 fee on top of face value
    'USD',
    '00000000-0000-0000-0000-000000000035',
    now(),
    '00000000-0000-0000-0000-000000000035',
    now(),
    NULL
);

-- 23. resale_listings — Jordan Rivera (regular user) reselling their own ticket
INSERT INTO resale_listings (id, ticket_id, event_id, seller_id, asking_price_amount, asking_price_currency, status, listed_at, resolved_at, buyer_order_id, updated_at)
VALUES (
    '00000000-0000-0000-0000-000000000018',
    '00000000-0000-0000-0000-000000000014',
    '00000000-0000-0000-0000-000000000004',
    '00000000-0000-0000-0000-000000000001',
    5500,
    'USD',
    'ACTIVE',
    now(),
    NULL, -- not yet resolved: ACTIVE listing
    NULL, -- no buyer order yet: ACTIVE listing
    now()
);

-- 24. resale_purchase_idempotency_keys (id is caller-supplied, not generated) — Jordan Rivera (regular user)
INSERT INTO resale_purchase_idempotency_keys (id, buyer_id, listing_id, order_id, created_at)
VALUES (
    '00000000-0000-0000-0000-000000000019',
    '00000000-0000-0000-0000-000000000001',
    '00000000-0000-0000-0000-000000000018',
    NULL, -- purchase still in-flight against the ACTIVE listing above
    now()
);

-- 25. refund_policies — managed by Morgan Lee (organizer)
INSERT INTO refund_policies (id, event_id, rule_type, days_before_event, custom_terms, created_by, created_at, updated_by, updated_at, deleted_at)
VALUES (
    '00000000-0000-0000-0000-000000000020',
    '00000000-0000-0000-0000-000000000004',
    'REFUNDABLE_UNTIL_N_DAYS',
    7,
    NULL, -- only meaningful when rule_type = CUSTOM
    '00000000-0000-0000-0000-000000000035',
    now(),
    '00000000-0000-0000-0000-000000000035',
    now(),
    NULL
);

-- 26. refunds — initiated by Morgan Lee (organizer), for Jordan Rivera's order
INSERT INTO refunds (id, order_id, amount_amount, amount_currency, reason, initiated_by, status, created_at, updated_at)
VALUES (
    '00000000-0000-0000-0000-000000000021',
    '00000000-0000-0000-0000-000000000011',
    4500,
    'USD',
    'Event schedule conflict',
    '00000000-0000-0000-0000-000000000035', -- Morgan Lee (organizer)
    'COMPLETED',
    now(),
    now()
);

-- 27. payouts — no created_by column (system-generated only)
INSERT INTO payouts (id, organization_id, gross_amount, gross_currency, fees_amount, fees_currency, net_amount, net_currency, period_start, period_end, status, created_at, updated_at)
VALUES (
    '00000000-0000-0000-0000-000000000022',
    '00000000-0000-0000-0000-000000000002',
    4500,
    'USD',
    250,
    'USD',
    4250,
    'USD',
    (now() - interval '14 days')::date,
    (now() - interval '7 days')::date,
    'PAID',
    now(),
    now()
);

-- 28. waitlist_entries — Jordan Rivera (regular user)
INSERT INTO waitlist_entries (id, event_id, ticket_type_id, user_id, position, notified_at, offer_expires_at, created_at)
VALUES (
    '00000000-0000-0000-0000-000000000023',
    '00000000-0000-0000-0000-000000000004',
    '00000000-0000-0000-0000-000000000005',
    '00000000-0000-0000-0000-000000000001',
    1,
    now(),
    now() + interval '24 hours',
    now() - interval '3 days'
);

-- 29. check_in_configs — managed by Morgan Lee (organizer)
INSERT INTO check_in_configs (id, event_id, mode, offline_fallback_expiry_seconds, created_at, updated_at, updated_by)
VALUES (
    '00000000-0000-0000-0000-000000000024',
    '00000000-0000-0000-0000-000000000004',
    'STANDARD',
    300,
    now(),
    now(),
    '00000000-0000-0000-0000-000000000035'
);

-- 30. scanner_devices (id is app-assigned, not generated) — registered to
-- Casey Kim (check-in scanner persona)
INSERT INTO scanner_devices (id, event_id, device_label, status, created_at, created_by, updated_at, updated_by)
VALUES (
    '00000000-0000-0000-0000-000000000025',
    '00000000-0000-0000-0000-000000000004',
    'Main Entrance Scanner',
    'ACTIVE',
    now(),
    '00000000-0000-0000-0000-000000000036', -- Casey Kim (check-in staff)
    now(),
    '00000000-0000-0000-0000-000000000036'
);

-- 31. check_in_records
INSERT INTO check_in_records (id, ticket_id, source_type, source_id, result, scanned_at)
VALUES (
    '00000000-0000-0000-0000-000000000026',
    '00000000-0000-0000-0000-000000000014',
    'SCANNER_DEVICE',
    '00000000-0000-0000-0000-000000000025',
    'VALID',
    now()
);

-- 32. fallback_scan_records
INSERT INTO fallback_scan_records (id, event_id, raw_credential, captured_at, synced_at, reconciled_ticket_id)
VALUES (
    '00000000-0000-0000-0000-000000000027',
    '00000000-0000-0000-0000-000000000004',
    'seed-credential-token-0001', -- matches the seeded ticket's credential: a resolved fallback scan
    now() - interval '1 hour',
    now(),
    '00000000-0000-0000-0000-000000000014'
);

-- 33. notifications — Jordan Rivera (regular user)
INSERT INTO notifications (id, user_id, type, channel, related_object_type, related_object_id, status, sent_at, created_at)
VALUES (
    '00000000-0000-0000-0000-000000000028',
    '00000000-0000-0000-0000-000000000001',
    'ORDER_CONFIRMATION',
    'EMAIL',
    'orders',
    '00000000-0000-0000-0000-000000000011',
    'SENT',
    now(),
    now()
);

-- 34. disputes — Jordan Rivera (regular user) raises it, Alex Chen (admin) resolves it
INSERT INTO disputes (id, order_id, ticket_id, raised_by, status, reason, resolution, created_at, updated_at, updated_by)
VALUES (
    '00000000-0000-0000-0000-000000000029',
    '00000000-0000-0000-0000-000000000011',
    '00000000-0000-0000-0000-000000000014',
    '00000000-0000-0000-0000-000000000001', -- Jordan Rivera (regular user)
    'RESOLVED',
    'Ticket not received after purchase.',
    'Resent ticket confirmation email; buyer confirmed receipt.',
    now(),
    now(),
    '00000000-0000-0000-0000-000000000033' -- Alex Chen (admin)
);

-- 35. moderation_actions — performed by Alex Chen (admin)
-- Historical log entry - doesn't need to match the event's *current* status
-- (events.status is ON_SALE above; this just records a past action).
INSERT INTO moderation_actions (id, target_type, target_id, action, reason, previous_status, performed_by, created_at)
VALUES (
    '00000000-0000-0000-0000-000000000030',
    'EVENT',
    '00000000-0000-0000-0000-000000000004',
    'SUSPEND',
    'Routine compliance check.',
    'PUBLISHED',
    '00000000-0000-0000-0000-000000000033', -- Alex Chen (admin)
    now() - interval '10 days'
);

-- 36. audit_log_entries — Jordan Rivera's (regular user) own order-paid action
INSERT INTO audit_log_entries (id, actor_id, action, target_type, target_id, created_at)
VALUES (
    '00000000-0000-0000-0000-000000000031',
    '00000000-0000-0000-0000-000000000001',
    'order.paid',
    'orders',
    '00000000-0000-0000-0000-000000000011',
    now()
);

-- 37. refresh_tokens — Jordan Rivera (regular user)
INSERT INTO refresh_tokens (id, user_id, expires_at, revoked, created_by, created_at, updated_by, updated_at, deleted_at)
VALUES (
    '00000000-0000-0000-0000-000000000032',
    '00000000-0000-0000-0000-000000000001',
    now() + interval '30 days',
    FALSE,
    '00000000-0000-0000-0000-000000000001',
    now(),
    NULL,
    NULL,
    NULL
);

-- Note: revinfo / revchanges / revinfo_seq (from V3) are Envers audit-
-- framework scaffolding with no corresponding JPA entity in this codebase
-- - nothing to seed there.

COMMIT;
