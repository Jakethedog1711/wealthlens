-- V4: AI audit trail
-- Every LLM output is stored with the facts it was given, so it can be explained and reviewed.

CREATE TABLE ai_output (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    feature         VARCHAR(30)  NOT NULL
        CHECK (feature IN ('DRIFT_SUMMARY', 'CLIENT_LETTER', 'ANOMALY_EXPLANATION')),
    client_id       BIGINT       REFERENCES clients (id),   -- NULL if not client-specific
    input_facts     JSONB        NOT NULL,                  -- exact numbers sent to the LLM
    prompt_version  VARCHAR(20)  NOT NULL,                  -- e.g. 'drift-v1'
    model           VARCHAR(100) NOT NULL,                  -- which LLM produced it
    output_text     TEXT         NOT NULL,
    input_tokens    INTEGER,
    output_tokens   INTEGER,
    latency_ms      INTEGER,

    -- Human-in-the-loop: an advisor reviews before anything reaches a client
    review_status   VARCHAR(20)  NOT NULL DEFAULT 'PENDING'
        CHECK (review_status IN ('PENDING', 'APPROVED', 'REJECTED')),
    reviewed_by     VARCHAR(100),
    reviewed_at     TIMESTAMPTZ,

    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_ai_output_client ON ai_output (client_id, created_at);