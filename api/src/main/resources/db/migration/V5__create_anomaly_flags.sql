-- V5: Anomaly flags raised by the rules engine (Java AnomalyDetector).
-- Derived data: re-running the scan updates existing flags instead of duplicating them.

CREATE TABLE anomaly_flags (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    rule         VARCHAR(30) NOT NULL
        CHECK (rule IN ('STRUCTURING', 'RAPID_IN_OUT', 'DORMANT_THEN_SPIKE', 'PROFILE_MISMATCH')),
    client_id    BIGINT      NOT NULL REFERENCES clients (id),
    account_id   BIGINT      NOT NULL REFERENCES accounts (id),
    start_date   DATE        NOT NULL,
    end_date     DATE        NOT NULL,
    reason       TEXT        NOT NULL,   -- one plain-English sentence, written by Java (not the LLM)
    metrics      JSONB       NOT NULL,   -- the numbers that tripped the rule
    evidence     JSONB       NOT NULL,   -- the transactions involved
    detected_at  TIMESTAMPTZ NOT NULL DEFAULT now(),

    UNIQUE (rule, account_id, start_date)
);

CREATE INDEX idx_anomaly_flags_client ON anomaly_flags (client_id);
