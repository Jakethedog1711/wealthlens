# WealthLens

An AI-assisted wealth management platform for South African advisors: it ingests market data through an AWS pipeline, values client portfolios, detects allocation drift and suspicious transactions, and uses an LLM to explain the results in plain English, grounded only in calculated numbers.

> 🚧 Work in progress — built in public, October–December 2026.

## What it does

| Feature | Status |
|---|---|
| Portfolio valuation (holdings × price × FX) | ⏳ Planned |
| Allocation drift detection vs risk-profile targets | ⏳ Planned |
| Market data pipeline on AWS (S3 → Glue/Lambda → Athena) | ⏳ Planned |
| Power BI advisor dashboard | ⏳ Planned |
| AI Wealth Copilot: drift summaries and client letters | ⏳ Planned |
| Transaction anomaly flagging with compliance explanations | ⏳ Planned |
| AI audit trail (every output stores the facts it was given) | ⏳ Planned |

## Architecture

```
Market + transaction data (JSE prices, SARB rates, synthetic clients)
        ↓  AWS pipeline: S3 → Glue/Lambda → Athena
PostgreSQL (portfolios, transactions, analytics)
        ↓
Spring Boot API (Java)
   ├─ Wealth Copilot: drift analysis + client letters
   └─ Anomaly flagging + compliance explanations
        ↓
Power BI dashboard
```

More detail in [docs/architecture.md](docs/architecture.md).

## Tech stack

- **Backend:** Java 21, Spring Boot, Spring AI, Flyway, JUnit
- **Data:** PostgreSQL, Python, AWS S3, Glue, Lambda, Athena
- **Reporting:** Power BI
- **Infrastructure:** Docker

## Repository layout

```
api/        Spring Boot backend
pipeline/   Python data ingestion and synthetic data generation
docs/       Architecture, data model and roadmap
```

## Running locally

Requires Docker.

```bash
cp .env.example .env
docker compose up -d     # starts PostgreSQL on localhost:5432
```

## Design decisions

- **Transactions are the source of truth.** Holdings, values and drift are derived from them, never edited directly.
- **The AI never calculates.** Java computes every figure; the LLM only explains numbers it is given, and each output is logged with its inputs.
- **All client data is synthetic.** No real personal information is used.

## Author

Ath'anathi Kepeyi — BSc IT (Software Engineering), Eduvos
