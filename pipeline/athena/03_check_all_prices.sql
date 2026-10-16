-- Sanity check: should match what's in Postgres (2,871 rows over 261 trading days).
-- No WHERE on dt, so Athena reads EVERY file: compare "Data scanned" with query 04.
SELECT COUNT(*)               AS price_rows,
       COUNT(DISTINCT dt)     AS trading_days,
       COUNT(DISTINCT ticker) AS instruments,
       MIN(price_date)        AS first_day,
       MAX(price_date)        AS last_day
FROM wealthlens.raw_prices
