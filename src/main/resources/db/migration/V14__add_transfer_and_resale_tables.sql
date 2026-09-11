-- Phase 7 — Ticket Transfer & Resale.
--
-- No FK constraints anywhere, matching every other table in this schema
-- (see V6/V7/V8/V9/V10/V11/V12/V13).

-- BR-TRANSFER-005: every transfer/resale must invalidate the previous
-- credential and issue a new one, but the ticket ROW itself persists across
-- transfers (same ticket_id - see ERD's "Ticket ||--o{ TicketTransfer:
-- ownership history"), and TicketCredentialService's credential is a pure
-- function of its inputs (deliberately re-derivable, not separately stored
-- state - Phase 6a). Bumping this counter and folding it into the signed
-- payload is what makes "regenerate the credential for the same ticket id"
-- actually produce a different string. Starts at 0 for every ticket issued
-- at checkout (Phase 6a's issuance path is unchanged).
ALTER TABLE tickets ADD COLUMN credential_version INT NOT NULL DEFAULT 0;

-- Immutable, append-only historical tier per the ERD's audit-column policy:
-- a single creation timestamp (transferred_at) and nothing else.
CREATE TABLE ticket_transfers (
    id              UUID            PRIMARY KEY,
    ticket_id       UUID            NOT NULL,
    from_user_id    UUID            NOT NULL,
    to_user_id      UUID            NOT NULL,
    source          VARCHAR(20)     NOT NULL,
    transferred_at  TIMESTAMPTZ     NOT NULL
);

CREATE INDEX idx_ticket_transfers_ticket_id ON ticket_transfers (ticket_id);

-- Managed-resource tier per the ERD's audit-column policy: full audit set,
-- same as ticket_templates/ticket_types/promo_codes. Deliberately given its
-- own generated id + a unique event_id column, rather than the ERD's
-- literal "event_id as primary key" depiction - no other table in this
-- schema uses a borrowed/shared primary key, and CLAUDE.md is explicit that
-- docs/ describe the target design, not a binding schema (see
-- TicketTemplate's own id despite also being event-scoped).
CREATE TABLE resale_policies (
    id              UUID            PRIMARY KEY,
    event_id        UUID            NOT NULL,
    enabled         BOOLEAN         NOT NULL DEFAULT FALSE,
    price_cap_rule  VARCHAR(30),
    fee_amount      BIGINT,
    fee_currency    VARCHAR(3),
    created_by      VARCHAR(255)    NOT NULL,
    created_at      TIMESTAMPTZ     NOT NULL,
    updated_by      VARCHAR(255),
    updated_at      TIMESTAMPTZ,
    deleted_at      TIMESTAMPTZ
);

-- One policy per event ("Event ||--o| ResalePolicy: has").
CREATE UNIQUE INDEX uq_resale_policies_event_id ON resale_policies (event_id);

-- Transactional/status-bearing tier per the ERD's audit-column policy:
-- created_at (listed_at)/updated_at only, no created_by/updated_by/deleted_at
-- (same tier as Order/Ticket/Payment/Cart/Seat).
CREATE TABLE resale_listings (
    id                      UUID            PRIMARY KEY,
    ticket_id               UUID            NOT NULL,
    event_id                UUID            NOT NULL,
    seller_id               UUID            NOT NULL,
    asking_price_amount     BIGINT          NOT NULL,
    asking_price_currency   VARCHAR(3)      NOT NULL,
    status                  VARCHAR(20)     NOT NULL,
    listed_at               TIMESTAMPTZ     NOT NULL,
    resolved_at             TIMESTAMPTZ,
    buyer_order_id          UUID,
    updated_at              TIMESTAMPTZ
);

-- BR "a ticket has at most one active resale listing at a time" (ERD note) -
-- DB-level backstop, same defense-in-depth idiom as V11's partial unique
-- index on orders.cart_id.
CREATE UNIQUE INDEX uq_resale_listings_active_ticket ON resale_listings (ticket_id) WHERE status = 'ACTIVE';

CREATE INDEX idx_resale_listings_event_id ON resale_listings (event_id);
CREATE INDEX idx_resale_listings_seller_id ON resale_listings (seller_id);

-- Purely internal bookkeeping for POST /resale-listings/{listingId}/purchase's
-- required Idempotency-Key header (BR-NFR-008), mirroring
-- checkout_idempotency_keys (V11) exactly but scoped to listing_id instead
-- of cart_id - kept as its own table rather than widening
-- checkout_idempotency_keys so Phase 5b's already-shipped cart-checkout
-- schema/tests stay untouched.
CREATE TABLE resale_purchase_idempotency_keys (
    id              UUID            PRIMARY KEY,
    buyer_id        UUID            NOT NULL,
    listing_id      UUID            NOT NULL,
    order_id        UUID,
    created_at      TIMESTAMPTZ     NOT NULL
);
