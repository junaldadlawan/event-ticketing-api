-- Phase 5a — Cart, CartItem & PromoCode (Cart/PromoCode entities and
-- inventory-hold mechanics; checkout/Order/Payment are a later dispatch).
--
-- No FK constraints anywhere, matching every other table in this schema
-- (see V6/V7/V8/V9).

-- Transactional/status-bearing tier per the ERD's audit-column policy:
-- created_at/updated_at only, no created_by/updated_by/deleted_at, no
-- status column (see Cart.java javadoc for why).
CREATE TABLE carts (
    id                  UUID            PRIMARY KEY,
    buyer_id            UUID            NOT NULL,
    promo_code_id       UUID,
    created_at          TIMESTAMPTZ     NOT NULL,
    updated_at          TIMESTAMPTZ
);

-- Immutable, append-only tier per the ERD's audit-column policy: only a
-- creation timestamp.
CREATE TABLE cart_items (
    id                  UUID            PRIMARY KEY,
    cart_id             UUID            NOT NULL,
    ticket_type_id      UUID            NOT NULL,
    seat_id             UUID,
    quantity            INT             NOT NULL,
    hold_expires_at     TIMESTAMPTZ     NOT NULL,
    created_at          TIMESTAMPTZ     NOT NULL
);

-- Managed-resource tier per the ERD's audit-column policy: full audit set,
-- same as ticket_types/seat_maps (V9).
CREATE TABLE promo_codes (
    id                      UUID            PRIMARY KEY,
    event_id                UUID            NOT NULL,
    code                    VARCHAR(50)     NOT NULL,
    discount_type           VARCHAR(20)     NOT NULL,
    discount_value          NUMERIC(12,2)   NOT NULL,
    usage_limit_total       INT,
    usage_limit_per_buyer   INT,
    valid_from              TIMESTAMPTZ     NOT NULL,
    valid_until             TIMESTAMPTZ     NOT NULL,
    created_by              VARCHAR(255)    NOT NULL,
    created_at              TIMESTAMPTZ     NOT NULL,
    updated_by              VARCHAR(255),
    updated_at              TIMESTAMPTZ,
    deleted_at              TIMESTAMPTZ,
    CONSTRAINT uq_promo_codes_event_code UNIQUE (event_id, code)
);

CREATE TABLE promo_code_applicable_ticket_types (
    promo_code_id       UUID            NOT NULL,
    ticket_type_id      UUID            NOT NULL,
    PRIMARY KEY (promo_code_id, ticket_type_id)
);
