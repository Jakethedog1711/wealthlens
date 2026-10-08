# Roadmap

Target: portfolio-ready by mid-December 2026. Study AWS Data Engineer Associate (DEA-C01) alongside, about 1 hour a day.

## Week 1 — Foundation
- [x] Repo structure, README, Docker Postgres, architecture doc
- [ ] AWS account created, $5 budget alert set
- [ ] Flyway schema migrations (core, market data, analytics, AI audit)
- [ ] Synthetic client + transaction generator (Python), incl. a few suspicious patterns
- [ ] Spring Boot skeleton: `GET /clients`, `GET /clients/{id}`
- [ ] Portfolio valuation service + JUnit tests

## Week 2 — Drift + first pipeline
- [ ] Drift calculation service + tests, `GET /clients/{id}/drift`
- [ ] Pipeline v1: JSE prices → S3 (partitioned by date) → Postgres
- [ ] Athena table over S3 data
- [ ] Power BI allocation-vs-target dashboard
- [ ] README screenshots + LinkedIn post

## Weeks 3–6 — AI layer
- [ ] Wealth Copilot: drift summaries + client letters (Spring AI)
- [ ] `ai_output` audit trail
- [ ] Anomaly rules engine + compliance explanations
- [ ] Deploy API on AWS

## Weeks 7–10 — Data engineering depth
- [ ] Glue/Lambda transforms, SARB rates, FX
- [ ] Scheduled pipeline runs

## Weeks 11–12 — Polish
- [ ] Demo video, final README, CV update
- [ ] Sit DEA-C01 at a test centre
