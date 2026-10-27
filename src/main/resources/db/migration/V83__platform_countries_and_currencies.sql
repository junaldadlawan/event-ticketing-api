-- Admin-managed platform currencies and countries. USD is the reference currency: usd_rate is how many units of the
-- currency buy 1 USD (USD itself is 1). Rates come from the exchange-rate feed; a failed fetch keeps the last rate and
-- sets rate_stale. Checkout stays PHP-only for now; these tables drive setup and display.
CREATE TABLE supported_currencies (
    code             VARCHAR(3)       PRIMARY KEY CHECK (code ~ '^[A-Z]{3}$'),
    name             VARCHAR(100)     NOT NULL,
    symbol           VARCHAR(10)      NOT NULL,
    minor_units      SMALLINT         NOT NULL CHECK (minor_units BETWEEN 0 AND 4),
    enabled          BOOLEAN          NOT NULL,
    usd_rate         NUMERIC(24, 10)  CHECK (usd_rate > 0),
    rate_updated_at  TIMESTAMPTZ,
    rate_stale       BOOLEAN          NOT NULL DEFAULT FALSE,
    created_by       VARCHAR(255)     NOT NULL,
    created_at       TIMESTAMPTZ      NOT NULL,
    updated_by       VARCHAR(255),
    updated_at       TIMESTAMPTZ,
    deleted_at       TIMESTAMPTZ
);

CREATE TABLE supported_countries (
    code           VARCHAR(2)    PRIMARY KEY CHECK (code ~ '^[A-Z]{2}$'),
    name           VARCHAR(100)  NOT NULL,
    currency_code  VARCHAR(3)    NOT NULL,
    enabled        BOOLEAN       NOT NULL,
    created_by     VARCHAR(255)  NOT NULL,
    created_at     TIMESTAMPTZ   NOT NULL,
    updated_by     VARCHAR(255),
    updated_at     TIMESTAMPTZ,
    deleted_at     TIMESTAMPTZ
);

-- PHP has no rate until the first fetch (never invented): it shows as stale until then.
INSERT INTO supported_currencies (code, name, symbol, minor_units, enabled, usd_rate, rate_updated_at, rate_stale, created_by, created_at)
VALUES ('USD', 'US Dollar', '$', 2, TRUE, 1, now(), FALSE, 'system', now()),
       ('PHP', 'Philippine Peso', '₱', 2, TRUE, NULL, NULL, TRUE, 'system', now());

INSERT INTO supported_countries (code, name, currency_code, enabled, created_by, created_at)
VALUES ('PH', 'Philippines', 'PHP', TRUE, 'system', now());

-- An organization's country (chosen on application) and currency (the country's currency, fixed when approved).
ALTER TABLE organizations ADD COLUMN country VARCHAR(2);
ALTER TABLE organizations ADD COLUMN currency VARCHAR(3);
UPDATE organizations SET country = 'PH', currency = 'PHP';
ALTER TABLE organizations ALTER COLUMN country SET NOT NULL;
