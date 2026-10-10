-- V3: Analytics tables for allocation drift

-- Target mix per risk profile (reference data, part of the app's rules)
CREATE TABLE target_allocations (
    risk_profile   VARCHAR(20)  NOT NULL
        CHECK (risk_profile IN ('CONSERVATIVE', 'MODERATE', 'AGGRESSIVE')),
    asset_class    VARCHAR(20)  NOT NULL
        CHECK (asset_class IN ('LOCAL_EQUITY', 'GLOBAL_EQUITY', 'BOND', 'PROPERTY', 'CASH')),
    target_pct     NUMERIC(5,2) NOT NULL CHECK (target_pct BETWEEN 0 AND 100),
    tolerance_pct  NUMERIC(5,2) NOT NULL DEFAULT 5.00,  -- allowed drift in percentage points

    PRIMARY KEY (risk_profile, asset_class)
);

INSERT INTO target_allocations (risk_profile, asset_class, target_pct) VALUES
    ('CONSERVATIVE', 'LOCAL_EQUITY',  20.00),
    ('CONSERVATIVE', 'GLOBAL_EQUITY', 10.00),
    ('CONSERVATIVE', 'BOND',          45.00),
    ('CONSERVATIVE', 'PROPERTY',       5.00),
    ('CONSERVATIVE', 'CASH',          20.00),
    ('MODERATE',     'LOCAL_EQUITY',  35.00),
    ('MODERATE',     'GLOBAL_EQUITY', 20.00),
    ('MODERATE',     'BOND',          30.00),
    ('MODERATE',     'PROPERTY',       5.00),
    ('MODERATE',     'CASH',          10.00),
    ('AGGRESSIVE',   'LOCAL_EQUITY',  45.00),
    ('AGGRESSIVE',   'GLOBAL_EQUITY', 35.00),
    ('AGGRESSIVE',   'BOND',          10.00),
    ('AGGRESSIVE',   'PROPERTY',       5.00),
    ('AGGRESSIVE',   'CASH',           5.00);

-- Calculated by the Java drift service. Derived output, never edited by hand.
CREATE TABLE allocation_snapshots (
    client_id      BIGINT        NOT NULL REFERENCES clients (id),
    snapshot_date  DATE          NOT NULL,
    asset_class    VARCHAR(20)   NOT NULL,
    market_value   NUMERIC(19,4) NOT NULL,  -- in ZAR
    actual_pct     NUMERIC(7,4)  NOT NULL,
    target_pct     NUMERIC(5,2)  NOT NULL,
    drift_pct      NUMERIC(7,4)  NOT NULL,  -- actual_pct - target_pct
    calculated_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),

    PRIMARY KEY (client_id, snapshot_date, asset_class)
);