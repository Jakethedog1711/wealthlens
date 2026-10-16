-- September 2026 per instrument. The WHERE on dt (the partition column) means Athena only
-- opens September's folders: "partition pruning". Compare "Data scanned" with query 03.
SELECT ticker,
       MIN(close_price)                                    AS low,
       MAX(close_price)                                    AS high,
       MAX_BY(close_price, price_date)                     AS month_end_close,
       ROUND((MAX_BY(close_price, price_date) / MIN_BY(close_price, price_date) - 1) * 100, 2)
                                                           AS month_return_pct
FROM wealthlens.raw_prices
WHERE dt BETWEEN '2026-09-01' AND '2026-09-30'
GROUP BY ticker
ORDER BY month_return_pct DESC
