-- Run this and 06 and compare "Data scanned": same question, CSV vs Parquet.
SELECT ticker, ROUND(AVG(close_price), 2) AS avg_close
FROM wealthlens.curated_prices
GROUP BY ticker
ORDER BY ticker
