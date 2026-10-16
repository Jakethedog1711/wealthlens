# Roadmap

Target: **project portfolio-ready by 20 November 2026** (compressed from mid-December on 13 Oct, after Weeks 1–2 moved faster than planned).
The AWS Data Engineer Associate (DEA-C01) runs on its own track: about 1 hour a day of study, exam in early-to-mid December.
Dates are re-checked at the end of each week.

## Week 1 — Foundation ✅

* \[x] Repo structure, README, Docker Postgres, architecture doc
* \[x] AWS account created, $5 budget alert set
* \[x] Flyway schema migrations (core, market data, analytics, AI audit)
* \[x] Synthetic client + transaction generator (Python), incl. a few suspicious patterns
* \[x] Spring Boot skeleton: `GET /clients`, `GET /clients/{id}`
* \[x] Portfolio valuation service + JUnit tests

## Week 2 (13–16 Oct) — Drift + first pipeline

* \[x] Drift calculation service + tests, `GET /clients/{id}/drift`
* \[x] Pipeline v1: JSE prices → S3 (partitioned by date) → Postgres
* \[x] Rotate the AWS access key for `wealthlens-dev`
* \[ ] Study notes: everything built so far (architecture, files, decisions, interview Q&A)
* \[x] Athena table over S3 data
* \[x] Power BI allocation-vs-target dashboard (built 16 Oct; visual polish still to do)
* \[ ] README screenshots + LinkedIn post

## Week 3 — Wealth Copilot (AI layer, part 1) ✅ (done 16–17 Oct, ahead of the 19–23 Oct plan)

* \[x] Choose LLM + API access; Spring AI set-up (Amazon Bedrock, Nova 2 Lite via EU inference profile)
* \[x] Drift summaries: the LLM explains numbers Java already calculated (it never calculates)
* \[x] Client letters, with advisor review before anything is "sent" (approve/reject, 409 on double review)
* \[x] `ai_output` audit trail: inputs, prompt version, model, tokens, review status

## Week 4 — Anomaly engine (AI layer, part 2) ✅ (done 17 Oct, ahead of the 26–30 Oct plan)

* \[x] Rules for the 4 planted patterns (structuring, rapid in-out, dormant spike, profile mismatch)
* \[x] Score the engine against `planted_anomalies.csv`: 12/12 caught, 0 false alarms (also checked on 20 other random datasets)
* \[x] LLM compliance explanations for each flag, logged to `ai_output`

## Week 5 (2–6 Nov) — Data engineering depth

* \[ ] Glue or Lambda transform: raw CSV → curated Parquet
* \[ ] Second source: SARB rates / USD-ZAR FX
* \[ ] Scheduled pipeline runs (EventBridge)

## Week 6 (9–13 Nov) — Deploy + polish

* \[ ] Deploy API on AWS (container, cloud database, HTTPS, within budget)
* \[ ] Demo video, final README, CV update

## Buffer (16–20 Nov) — Own the code

* \[ ] Make 2–3 small changes without AI help (e.g. filter clients by risk profile)
* \[ ] Practise explaining the architecture and key decisions out loud
* \[ ] Fix anything the deploy or earlier weeks pushed back

## DEA-C01 (separate track)

* \[ ] ~1 hour a day of study alongside the project
* \[ ] Book and sit the exam at a test centre (early-to-mid December)
