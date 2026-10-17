-- Compare with 06 (CSV) and 07 (small Parquet files) "Data scanned": same question, CSV vs Parquet.
SELECT ticker, ROUND(AVG(close_price), 2) AS avg_close
FROM wealthlens.curated_prices_compact
GROUP BY ticker
ORDER BY ticker
