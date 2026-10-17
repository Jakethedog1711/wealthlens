-- Run this and 07 and compare "Data scanned": same question, CSV vs Parquet.
SELECT ticker, ROUND(AVG(close_price), 2) AS avg_close
FROM wealthlens.raw_prices
GROUP BY ticker
ORDER BY ticker
