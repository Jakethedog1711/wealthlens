-- V2: Market data tables
-- Filled by the data pipeline (JSE prices, FX). Used for portfolio valuation.

-- Daily closing price per instrument
CREATE TABLE instrument_prices (
    instrument_id  BIGINT        NOT NULL REFERENCES instruments (id),
    price_date     DATE          NOT NULL,
    close_price    NUMERIC(19,6) NOT NULL CHECK (close_price > 0),
    currency       CHAR(3)       NOT NULL DEFAULT 'ZAR',
    source         VARCHAR(50)   NOT NULL,   -- e.g. 'SYNTHETIC', 'JSE_PIPELINE'
    loaded_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),

    -- One price per instrument per day
    PRIMARY KEY (instrument_id, price_date)
);

-- Daily FX rates: 1 unit of base_currency = rate units of quote_currency
-- e.g. base 'USD', quote 'ZAR', rate 18.20
CREATE TABLE fx_rates (
    rate_date       DATE          NOT NULL,
    base_currency   CHAR(3)       NOT NULL,
    quote_currency  CHAR(3)       NOT NULL,
    rate            NUMERIC(19,8) NOT NULL CHECK (rate > 0),
    source          VARCHAR(50)   NOT NULL,
    loaded_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),

    PRIMARY KEY (rate_date, base_currency, quote_currency),
    CHECK (base_currency <> quote_currency)
);