-- V1: Core tables for WealthLens
-- Transactions are the source of truth; holdings and values are derived from them.

-- Clients: the people an advisor manages (synthetic data only)
CREATE TABLE clients (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    first_name     VARCHAR(100) NOT NULL,
    last_name      VARCHAR(100) NOT NULL,
    email          VARCHAR(255) NOT NULL UNIQUE,
    date_of_birth  DATE         NOT NULL,
    risk_profile   VARCHAR(20)  NOT NULL
        CHECK (risk_profile IN ('CONSERVATIVE', 'MODERATE', 'AGGRESSIVE')),
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Accounts: a client can hold several (e.g. a TFSA and a retirement annuity)
CREATE TABLE accounts (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    client_id       BIGINT      NOT NULL REFERENCES clients (id),
    account_number  VARCHAR(20) NOT NULL UNIQUE,
    account_type    VARCHAR(20) NOT NULL
        CHECK (account_type IN ('DISCRETIONARY', 'TFSA', 'RA')),
    base_currency   CHAR(3)     NOT NULL DEFAULT 'ZAR',
    opened_on       DATE        NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Instruments: what can be bought and sold
CREATE TABLE instruments (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    ticker       VARCHAR(20)  NOT NULL UNIQUE,
    name         VARCHAR(200) NOT NULL,
    asset_class  VARCHAR(20)  NOT NULL
        CHECK (asset_class IN ('LOCAL_EQUITY', 'GLOBAL_EQUITY', 'BOND', 'PROPERTY', 'CASH')),
    exchange     VARCHAR(20)  NOT NULL DEFAULT 'JSE',
    currency     CHAR(3)      NOT NULL DEFAULT 'ZAR',
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Transactions: every movement of money or units. The source of truth.
CREATE TABLE transactions (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    account_id     BIGINT        NOT NULL REFERENCES accounts (id),
    instrument_id  BIGINT        REFERENCES instruments (id),  -- NULL for cash movements
    txn_type       VARCHAR(20)   NOT NULL
        CHECK (txn_type IN ('BUY', 'SELL', 'DEPOSIT', 'WITHDRAWAL', 'DIVIDEND', 'FEE')),
    trade_date     DATE          NOT NULL,
    quantity       NUMERIC(19,6),                              -- units; only for BUY/SELL
    price          NUMERIC(19,6),                              -- per unit; only for BUY/SELL
    amount         NUMERIC(19,4) NOT NULL CHECK (amount > 0),  -- always positive; txn_type gives direction
    currency       CHAR(3)       NOT NULL DEFAULT 'ZAR',
    description    VARCHAR(255),
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),

    -- Each transaction type must have the right fields filled in
    CONSTRAINT chk_txn_shape CHECK (
        (txn_type IN ('BUY', 'SELL')
            AND instrument_id IS NOT NULL AND quantity > 0 AND price > 0)
     OR (txn_type = 'DIVIDEND'
            AND instrument_id IS NOT NULL AND quantity IS NULL AND price IS NULL)
     OR (txn_type IN ('DEPOSIT', 'WITHDRAWAL', 'FEE')
            AND instrument_id IS NULL AND quantity IS NULL AND price IS NULL)
    )
);

-- Indexes: speed up the queries we know we'll run often
CREATE INDEX idx_accounts_client         ON accounts (client_id);
CREATE INDEX idx_transactions_account    ON transactions (account_id, trade_date);
CREATE INDEX idx_transactions_instrument ON transactions (instrument_id);