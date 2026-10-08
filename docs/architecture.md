# Architecture

```mermaid
flowchart TD
    A[JSE prices / SARB rates] --> B[Python ingestion]
    S[Synthetic client generator] --> DB
    B --> C[(S3 raw, partitioned by date)]
    C --> D[Glue / Lambda clean + transform]
    D --> E[(S3 curated)]
    E --> F[Athena]
    E --> DB[(PostgreSQL)]
    DB --> API[Spring Boot API]
    API --> V[Valuation + drift services]
    API --> X[Anomaly rules engine]
    V --> AI[Wealth Copilot - LLM]
    X --> AI
    AI --> LOG[(ai_output audit trail)]
    DB --> PBI[Power BI dashboard]
```

## Principles

1. **Transactions are the source of truth.** Holdings and values are derived.
2. **Deterministic first, AI second.** Java calculates; the LLM explains.
3. **Every AI output is auditable.** Inputs, prompt version and model are stored with the text.
4. **Synthetic data only.** No real client information.
