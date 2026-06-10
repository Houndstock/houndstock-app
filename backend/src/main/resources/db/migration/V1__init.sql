-- Initial Houndstock schema.
--
-- Design notes:
-- - Stocks are keyed by ISIN — the only stable cross-AMC identifier.
-- - Schemes are owned by an AMC. mfapi_scheme_code is nullable because we
--   haven't built the AMC-scheme ↔ mfapi.in mapping yet.
-- - Holdings are point-in-time facts keyed by (scheme, stock, as_of_date)
--   so we can do month-over-month diffs without losing history.
-- - portfolio_publishes is the audit trail: which file did we ingest, when,
--   for which (amc, as_of_date) pair.

CREATE TABLE amcs (
    id          SERIAL PRIMARY KEY,
    slug        VARCHAR(64)  NOT NULL UNIQUE,
    name        VARCHAR(128) NOT NULL UNIQUE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE stocks (
    isin        VARCHAR(12)  PRIMARY KEY,
    name        VARCHAR(255) NOT NULL,
    industry    VARCHAR(128),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_stocks_name ON stocks (lower(name));

CREATE TABLE schemes (
    id                  SERIAL       PRIMARY KEY,
    amc_id              INT          NOT NULL REFERENCES amcs(id) ON DELETE CASCADE,
    name                VARCHAR(255) NOT NULL,
    short_code          VARCHAR(32),                     -- AMC's internal code (e.g. PPFCF)
    scheme_type         VARCHAR(64),                     -- "Open Ended" / "Close Ended"
    scheme_category     VARCHAR(128),                    -- "Flexi Cap" / "Large Cap" / etc.
    mfapi_scheme_code   BIGINT       UNIQUE,             -- nullable until mapped
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (amc_id, name)
);

CREATE INDEX idx_schemes_name ON schemes (lower(name));

-- Section taxonomy (a CHECK so typos surface immediately).
CREATE TYPE holding_section AS ENUM (
    'equity_listed',
    'equity_unlisted',
    'equity_foreign',
    'debt_listed',
    'debt_unlisted',
    'debt_money_market',
    'derivatives',
    'cash_and_equivalents',
    'reits_invits',
    'other'
);

CREATE TABLE holdings (
    scheme_id           INT             NOT NULL REFERENCES schemes(id) ON DELETE CASCADE,
    stock_isin          VARCHAR(12)     NOT NULL REFERENCES stocks(isin),
    as_of_date          DATE            NOT NULL,
    weight_pct          NUMERIC(10, 6)  NOT NULL,  -- 0..100
    market_value_lakhs  NUMERIC(18, 2),            -- Rs in lakhs
    quantity            BIGINT,
    section             holding_section NOT NULL,
    PRIMARY KEY (scheme_id, stock_isin, as_of_date)
);

CREATE INDEX idx_holdings_stock_date ON holdings (stock_isin, as_of_date);
CREATE INDEX idx_holdings_scheme_date ON holdings (scheme_id, as_of_date);

CREATE TABLE portfolio_publishes (
    id              SERIAL       PRIMARY KEY,
    amc_id          INT          NOT NULL REFERENCES amcs(id),
    as_of_date      DATE         NOT NULL,
    source_url      TEXT         NOT NULL,
    file_sha256     CHAR(64),
    fetched_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    holdings_count  INT,
    UNIQUE (amc_id, as_of_date)
);
